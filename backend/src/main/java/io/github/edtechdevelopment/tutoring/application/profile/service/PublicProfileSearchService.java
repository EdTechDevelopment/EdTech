package io.github.edtechdevelopment.tutoring.application.profile.service;

import io.github.edtechdevelopment.tutoring.api.model.profile.search.StudentProfileCardView;
import io.github.edtechdevelopment.tutoring.api.model.profile.search.TeacherProfileCardView;
import io.github.edtechdevelopment.tutoring.api.query.TutoringSubjectQuery;
import io.github.edtechdevelopment.tutoring.application.integration.identity.port.out.IdentityAccountGateway;
import io.github.edtechdevelopment.tutoring.application.profile.exception.InvalidPublicProfileCursorException;
import io.github.edtechdevelopment.tutoring.application.profile.exception.ProfileReadConsistencyException;
import io.github.edtechdevelopment.tutoring.application.profile.mapper.ProfileViewMapper;
import io.github.edtechdevelopment.tutoring.application.profile.model.query.PublicProfilePageCursor;
import io.github.edtechdevelopment.tutoring.application.profile.model.query.PublicProfileSearchCandidate;
import io.github.edtechdevelopment.tutoring.application.profile.model.query.PublicProfileSearchQuery;
import io.github.edtechdevelopment.tutoring.application.profile.model.result.PublicStudentProfilePageResult;
import io.github.edtechdevelopment.tutoring.application.profile.model.result.PublicTeacherProfilePageResult;
import io.github.edtechdevelopment.tutoring.application.profile.port.in.SearchPublicProfilesUseCase;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.PublicProfileCursorCodec;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.StudentProfileRepository;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.TeacherProfileRepository;
import io.github.edtechdevelopment.tutoring.domain.profile.model.ProfileType;
import io.github.edtechdevelopment.tutoring.domain.profile.model.StudentProfile;
import io.github.edtechdevelopment.tutoring.domain.profile.model.TeacherProfile;
import io.github.edtechdevelopment.tutoring.domain.subject.model.SubjectCode;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Transactional(readOnly = true)
public class PublicProfileSearchService implements SearchPublicProfilesUseCase {

    private static final int PAGE_SIZE = 20;
    private static final int FETCH_LIMIT = PAGE_SIZE + 1;

    private final TeacherProfileRepository teacherRepository;
    private final StudentProfileRepository studentRepository;
    private final IdentityAccountGateway identityGateway;
    private final TutoringSubjectQuery subjectQuery;
    private final PublicProfileCursorCodec cursorCodec;
    private final ProfileViewMapper mapper;
    private final Clock clock;

    public PublicProfileSearchService(
            TeacherProfileRepository teacherRepository, StudentProfileRepository studentRepository,
            IdentityAccountGateway identityGateway, TutoringSubjectQuery subjectQuery,
            PublicProfileCursorCodec cursorCodec, ProfileViewMapper mapper, Clock clock) {

        this.teacherRepository = Objects.requireNonNull(teacherRepository, "Teacher repository must not be null");
        this.studentRepository = Objects.requireNonNull(studentRepository, "Student repository must not be null");
        this.identityGateway = Objects.requireNonNull(identityGateway, "Identity gateway must not be null");
        this.subjectQuery = Objects.requireNonNull(subjectQuery, "Subject query must not be null");
        this.cursorCodec = Objects.requireNonNull(cursorCodec, "Cursor codec must not be null");
        this.mapper = Objects.requireNonNull(mapper, "Profile view mapper must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
    }

    @Override
    public PublicTeacherProfilePageResult searchTeachers(PublicProfileSearchQuery query) {
        Objects.requireNonNull(query, "Search query must not be null");
        CandidatePage page = findCandidatePage(query, ProfileType.TEACHER);
        Set<UUID> ids = userIds(page.candidates());
        Map<UUID, TeacherProfile> profiles = ids.isEmpty() ? Map.of() : teacherRepository.findByUserIds(ids);
        List<TeacherProfileCardView> views = new ArrayList<>(page.candidates().size());
        for (PublicProfileSearchCandidate candidate : page.candidates()) {
            TeacherProfile profile = profiles.get(candidate.userId());
            if (profile == null) {
                throw new ProfileReadConsistencyException(candidate.userId(), "Teacher profile disappeared during public search");
            }
            views.add(mapper.toCard(profile, page.ages().get(candidate.userId())));
        }
        return new PublicTeacherProfilePageResult(views, page.nextCursor());
    }

    @Override
    public PublicStudentProfilePageResult searchStudents(PublicProfileSearchQuery query) {
        Objects.requireNonNull(query, "Search query must not be null");
        CandidatePage page = findCandidatePage(query, ProfileType.STUDENT);
        Set<UUID> ids = userIds(page.candidates());
        Map<UUID, StudentProfile> profiles = ids.isEmpty() ? Map.of() : studentRepository.findByUserIds(ids);
        List<StudentProfileCardView> views = new ArrayList<>(page.candidates().size());
        for (PublicProfileSearchCandidate candidate : page.candidates()) {
            StudentProfile profile = profiles.get(candidate.userId());
            if (profile == null) {
                throw new ProfileReadConsistencyException(candidate.userId(), "Student profile disappeared during public search");
            }
            views.add(mapper.toCard(profile, page.ages().get(candidate.userId())));
        }
        return new PublicStudentProfilePageResult(views, page.nextCursor());
    }

    private CandidatePage findCandidatePage(PublicProfileSearchQuery query, ProfileType type) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        PublicProfilePageCursor after = query.cursor() == null ? null : cursorCodec.decode(query.cursor(), type, query);
        LocalDate ageAsOf = after == null ? today : after.ageAsOf();
        if (ageAsOf.isAfter(today)) {
            throw new InvalidPublicProfileCursorException();
        }
        if (query.subjectCode() != null) {
            subjectQuery.requireAllExist(Set.of(query.subjectCode()));
        }

        List<PublicProfileSearchCandidate> matches = new ArrayList<>(FETCH_LIMIT);
        Map<UUID, Integer> matchAges = new HashMap<>();
        PublicProfilePageCursor lastReturned = null;

        while (matches.size() <= PAGE_SIZE) {
            List<PublicProfileSearchCandidate> candidates = findCandidates(type, query, after, FETCH_LIMIT);
            if (candidates.isEmpty()) {
                break;
            }

            Set<UUID> candidateIds = userIds(candidates);
            Set<UUID> activeIds = identityGateway.findActiveUserIds(candidateIds);
            Map<UUID, Integer> ages = activeIds.isEmpty() ? Map.of() : identityGateway.findAgesByIds(activeIds, ageAsOf);

            for (PublicProfileSearchCandidate candidate : candidates) {
                after = new PublicProfilePageCursor(candidate.createdAt(), candidate.userId(), ageAsOf);
                if (!activeIds.contains(candidate.userId())) {
                    continue;
                }

                Integer age = ages.get(candidate.userId());
                if (age == null || age < 0) {
                    throw new ProfileReadConsistencyException(candidate.userId(), "Profile account age is missing or invalid");
                }
                if ((query.minAge() != null && age < query.minAge())
                        || (query.maxAge() != null && age > query.maxAge())) {
                    continue;
                }

                matches.add(candidate);
                matchAges.put(candidate.userId(), age);
                if (matches.size() <= PAGE_SIZE) {
                    lastReturned = after;
                } else {
                    break;
                }
            }

            if (candidates.size() < FETCH_LIMIT) {
                break;
            }
        }

        String nextCursor = null;
        if (matches.size() > PAGE_SIZE) {
            matches.removeLast();
            nextCursor = cursorCodec.encode(lastReturned, type, query);
        }
        Map<UUID, Integer> pageAges = new HashMap<>();
        for (PublicProfileSearchCandidate match : matches) {
            pageAges.put(match.userId(), matchAges.get(match.userId()));
        }
        return new CandidatePage(matches, pageAges, nextCursor);
    }

    private List<PublicProfileSearchCandidate> findCandidates(
            ProfileType type, PublicProfileSearchQuery query, PublicProfilePageCursor after, int fetchLimit) {

        SubjectCode subject = query.subjectCode() == null ? null : new SubjectCode(query.subjectCode());
        if (type == ProfileType.TEACHER) {
            return teacherRepository.findSearchCandidates(subject, after, fetchLimit);
        }
        return studentRepository.findSearchCandidates(subject, after, fetchLimit);
    }

    private static Set<UUID> userIds(List<PublicProfileSearchCandidate> candidates) {
        Set<UUID> ids = new HashSet<>();
        for (PublicProfileSearchCandidate candidate : candidates) {
            ids.add(candidate.userId());
        }
        return Set.copyOf(ids);
    }

    private record CandidatePage(
            List<PublicProfileSearchCandidate> candidates,
            Map<UUID, Integer> ages,
            String nextCursor) {

        private CandidatePage {
            candidates = List.copyOf(candidates);
            ages = Map.copyOf(ages);
        }
    }
}
