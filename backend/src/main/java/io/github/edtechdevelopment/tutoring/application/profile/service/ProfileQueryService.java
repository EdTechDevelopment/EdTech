package io.github.edtechdevelopment.tutoring.application.profile.service;

import io.github.edtechdevelopment.tutoring.api.exception.ProfileNotFoundException;
import io.github.edtechdevelopment.tutoring.api.model.profile.ProfileTypeView;
import io.github.edtechdevelopment.tutoring.api.model.profile.StudentProfileView;
import io.github.edtechdevelopment.tutoring.api.model.profile.TeacherProfileView;
import io.github.edtechdevelopment.tutoring.api.model.profile.summary.StudentProfileSummary;
import io.github.edtechdevelopment.tutoring.api.model.profile.summary.TeacherProfileSummary;
import io.github.edtechdevelopment.tutoring.api.query.TutoringProfileQuery;
import io.github.edtechdevelopment.tutoring.application.integration.identity.port.out.IdentityAccountGateway;
import io.github.edtechdevelopment.tutoring.application.profile.exception.ProfileReadConsistencyException;
import io.github.edtechdevelopment.tutoring.application.profile.mapper.ProfileViewMapper;
import io.github.edtechdevelopment.tutoring.application.profile.model.query.ProfileNameData;
import io.github.edtechdevelopment.tutoring.application.profile.port.in.GetPublicProfileUseCase;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.StudentProfileRepository;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.TeacherProfileRepository;
import io.github.edtechdevelopment.tutoring.application.relationship.port.out.TeacherStudentRepository;
import io.github.edtechdevelopment.tutoring.domain.profile.model.StudentProfile;
import io.github.edtechdevelopment.tutoring.domain.profile.model.TeacherProfile;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Transactional(readOnly = true)
public class ProfileQueryService implements TutoringProfileQuery, GetPublicProfileUseCase {

    private final TeacherProfileRepository teacherRepository;
    private final StudentProfileRepository studentRepository;
    private final TeacherStudentRepository relationshipRepository;
    private final IdentityAccountGateway identityGateway;
    private final ProfileViewMapper mapper;
    private final Clock clock;

    public ProfileQueryService(
            TeacherProfileRepository teacherRepository, StudentProfileRepository studentRepository,
            TeacherStudentRepository relationshipRepository, IdentityAccountGateway identityGateway,
            ProfileViewMapper mapper, Clock clock) {

        this.teacherRepository = Objects.requireNonNull(teacherRepository, "Teacher repository must not be null");
        this.studentRepository = Objects.requireNonNull(studentRepository, "Student repository must not be null");
        this.relationshipRepository = Objects.requireNonNull(relationshipRepository, "Relationship repository must not be null");
        this.identityGateway = Objects.requireNonNull(identityGateway, "Identity gateway must not be null");
        this.mapper = Objects.requireNonNull(mapper, "Profile view mapper must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
    }

    @Override
    public Optional<TeacherProfileView> findTeacherProfile(UUID userId) {
        Objects.requireNonNull(userId, "User id must not be null");
        return teacherRepository.findByUserId(userId).map(profile -> mapper.toView(profile, requireAge(userId)));
    }

    @Override
    public Optional<StudentProfileView> findStudentProfile(UUID userId) {
        Objects.requireNonNull(userId, "User id must not be null");
        return studentRepository.findByUserId(userId).map(profile -> mapper.toView(profile, requireAge(userId)));
    }

    @Override
    public Optional<TeacherProfileView> findPublicTeacherProfile(UUID userId) {
        Objects.requireNonNull(userId, "User id must not be null");
        return teacherRepository.findByUserId(userId).flatMap(this::toPublicTeacherIfActive);
    }

    @Override
    public Optional<StudentProfileView> findPublicStudentProfile(UUID userId) {
        Objects.requireNonNull(userId, "User id must not be null");
        return studentRepository.findByUserId(userId).flatMap(this::toPublicStudentIfActive);
    }

    @Override
    public void requireTeacherProfile(UUID teacherUserId) {
        Objects.requireNonNull(teacherUserId, "Teacher user id must not be null");
        if (!teacherRepository.existsByUserId(teacherUserId)) {
            throw new ProfileNotFoundException(ProfileTypeView.TEACHER, teacherUserId);
        }
    }

    @Override
    public Map<UUID, TeacherProfileSummary> findTeacherSummaries(Set<UUID> teacherUserIds) {
        Set<UUID> ids = requireIds(teacherUserIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, TeacherProfileSummary> summaries = new HashMap<>();
        for (ProfileNameData row : teacherRepository.findSummariesByUserIds(ids).values()) {
            summaries.put(row.userId(), mapper.toTeacherSummary(row));
        }
        return Map.copyOf(summaries);
    }

    @Override
    public Map<UUID, StudentProfileSummary> findStudentSummaries(Set<UUID> studentUserIds) {
        Set<UUID> ids = requireIds(studentUserIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, StudentProfileSummary> summaries = new HashMap<>();
        for (ProfileNameData row : studentRepository.findSummariesByUserIds(ids).values()) {
            summaries.put(row.userId(), mapper.toStudentSummary(row));
        }
        return Map.copyOf(summaries);
    }

    @Override
    public Optional<StudentProfileView> findLinkedStudentProfile(UUID teacherUserId, UUID studentUserId) {
        Objects.requireNonNull(teacherUserId, "Teacher user id must not be null");
        Objects.requireNonNull(studentUserId, "Student user id must not be null");
        if (!relationshipRepository.exists(teacherUserId, studentUserId)) {
            return Optional.empty();
        }
        StudentProfile profile = studentRepository.findByUserId(studentUserId)
                .orElseThrow(() -> new ProfileReadConsistencyException(studentUserId, "Linked student profile is missing"));
        return toPublicStudentIfActive(profile);
    }

    @Override
    public Optional<TeacherProfileView> findLinkedTeacherProfile(UUID studentUserId, UUID teacherUserId) {
        Objects.requireNonNull(studentUserId, "Student user id must not be null");
        Objects.requireNonNull(teacherUserId, "Teacher user id must not be null");
        if (!relationshipRepository.exists(teacherUserId, studentUserId)) {
            return Optional.empty();
        }
        TeacherProfile profile = teacherRepository.findByUserId(teacherUserId)
                .orElseThrow(() -> new ProfileReadConsistencyException(teacherUserId, "Linked teacher profile is missing"));
        return toPublicTeacherIfActive(profile);
    }

    private Optional<TeacherProfileView> toPublicTeacherIfActive(TeacherProfile profile) {
        if (!identityGateway.findActiveUserIds(Set.of(profile.userId())).contains(profile.userId())) {
            return Optional.empty();
        }
        return Optional.of(mapper.toView(profile, requireAge(profile.userId())));
    }

    private Optional<StudentProfileView> toPublicStudentIfActive(StudentProfile profile) {
        if (!identityGateway.findActiveUserIds(Set.of(profile.userId())).contains(profile.userId())) {
            return Optional.empty();
        }
        return Optional.of(mapper.toView(profile, requireAge(profile.userId())));
    }

    private int requireAge(UUID userId) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        Integer age = identityGateway.findAgesByIds(Set.of(userId), today).get(userId);
        if (age == null || age < 0) {
            throw new ProfileReadConsistencyException(userId, "Profile account age is missing or invalid");
        }
        return age;
    }

    private static Set<UUID> requireIds(Set<UUID> ids) {
        if (ids == null || ids.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("User ids must not be null or contain null");
        }
        return Set.copyOf(ids);
    }
}
