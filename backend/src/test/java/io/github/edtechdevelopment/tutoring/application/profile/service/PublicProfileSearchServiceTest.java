package io.github.edtechdevelopment.tutoring.application.profile.service;

import io.github.edtechdevelopment.tutoring.api.exception.UnknownSubjectsException;
import io.github.edtechdevelopment.tutoring.api.query.TutoringSubjectQuery;
import io.github.edtechdevelopment.tutoring.application.integration.identity.port.out.IdentityAccountGateway;
import io.github.edtechdevelopment.tutoring.application.profile.exception.InvalidPublicProfileCursorException;
import io.github.edtechdevelopment.tutoring.application.profile.exception.ProfileReadConsistencyException;
import io.github.edtechdevelopment.tutoring.application.profile.mapper.ProfileViewMapper;
import io.github.edtechdevelopment.tutoring.application.profile.model.query.PublicProfilePageCursor;
import io.github.edtechdevelopment.tutoring.application.profile.model.query.PublicProfileSearchCandidate;
import io.github.edtechdevelopment.tutoring.application.profile.model.query.PublicProfileSearchQuery;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.PublicProfileCursorCodec;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.StudentProfileRepository;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.TeacherProfileRepository;
import io.github.edtechdevelopment.tutoring.domain.profile.model.ProfileType;
import io.github.edtechdevelopment.tutoring.domain.profile.model.StudentProfile;
import io.github.edtechdevelopment.tutoring.domain.profile.model.TeacherProfile;
import io.github.edtechdevelopment.tutoring.domain.subject.model.SubjectCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static io.github.edtechdevelopment.tutoring.application.profile.service.ProfileReadFixtures.CLOCK;
import static io.github.edtechdevelopment.tutoring.application.profile.service.ProfileReadFixtures.NOW;
import static io.github.edtechdevelopment.tutoring.application.profile.service.ProfileReadFixtures.TODAY;
import static io.github.edtechdevelopment.tutoring.application.profile.service.ProfileReadFixtures.id;
import static io.github.edtechdevelopment.tutoring.application.profile.service.ProfileReadFixtures.student;
import static io.github.edtechdevelopment.tutoring.application.profile.service.ProfileReadFixtures.teacher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PublicProfileSearchServiceTest {

    private static final int SEARCH_FETCH_LIMIT = 21;

    private final TeacherProfileRepository teachers = mock(TeacherProfileRepository.class);
    private final StudentProfileRepository students = mock(StudentProfileRepository.class);
    private final IdentityAccountGateway identity = mock(IdentityAccountGateway.class);
    private final TutoringSubjectQuery subjects = mock(TutoringSubjectQuery.class);
    private final PublicProfileCursorCodec codec = mock(PublicProfileCursorCodec.class);
    private final PublicProfileSearchService service = new PublicProfileSearchService(
            teachers, students, identity, subjects, codec, new ProfileViewMapper(), CLOCK);

    @Test
    void returnsTwentyTeacherCardsAndNextPageStartsAfterLastDisplayedCard() {
        List<PublicProfileSearchCandidate> candidates = IntStream.rangeClosed(1, 53)
                .mapToObj(this::candidate).toList();
        when(teachers.findSearchCandidates(eq(new SubjectCode("MATH")), any(), eq(SEARCH_FETCH_LIMIT)))
                .thenAnswer(call -> candidatesAfter(candidates, call.getArgument(1)));
        when(identity.findActiveUserIds(any())).thenAnswer(call -> {
            Set<UUID> activeIds = new HashSet<>(call.getArgument(0));
            activeIds.remove(id(2));
            return activeIds;
        });
        when(identity.findAgesByIds(any(), eq(TODAY))).thenAnswer(call -> {
            Map<UUID, Integer> ages = new HashMap<>();
            for (UUID userId : call.<Set<UUID>>getArgument(0)) {
                ages.put(userId, userId.equals(id(3)) ? 60 : 30);
            }
            return ages;
        });
        when(teachers.findByUserIds(any())).thenAnswer(call -> teacherProfiles(call.getArgument(0)));

        PublicProfileSearchQuery firstQuery = new PublicProfileSearchQuery("MATH", 25, 40, null);
        ArgumentCaptor<PublicProfilePageCursor> cursor = ArgumentCaptor.forClass(PublicProfilePageCursor.class);
        when(codec.encode(cursor.capture(), eq(ProfileType.TEACHER), eq(firstQuery))).thenReturn("next");

        var first = service.searchTeachers(firstQuery);

        assertEquals(IntStream.concat(IntStream.of(1), IntStream.rangeClosed(4, 22))
                .mapToObj(ProfileReadFixtures::id).toList(),
                first.items().stream().map(card -> card.userId()).toList());
        assertEquals(20, first.items().size());
        assertEquals("next", first.nextCursor());
        assertEquals(new PublicProfilePageCursor(candidates.get(21).createdAt(), id(22), TODAY), cursor.getValue());
        assertEquals("Teacher", first.items().getFirst().displayName());
        assertEquals("City", first.items().getFirst().city());
        assertEquals(Set.of("MATH"), first.items().getFirst().subjectCodes());
        assertEquals("Description", first.items().getFirst().descriptionPreview());
        assertEquals("https://example.com/photo.png", first.items().getFirst().photoUrl());
        verify(teachers).findSearchCandidates(new SubjectCode("MATH"), null, SEARCH_FETCH_LIMIT);
        verify(teachers).findSearchCandidates(new SubjectCode("MATH"),
                new PublicProfilePageCursor(candidates.get(20).createdAt(), id(21), TODAY), SEARCH_FETCH_LIMIT);
        verify(teachers).findByUserIds(IntStream.concat(IntStream.of(1), IntStream.rangeClosed(4, 22))
                .mapToObj(ProfileReadFixtures::id).collect(java.util.stream.Collectors.toSet()));
        verify(teachers, never()).findByUserIds(Set.of(id(23)));

        PublicProfileSearchQuery nextQuery = new PublicProfileSearchQuery("MATH", 25, 40, "next");
        when(codec.decode("next", ProfileType.TEACHER, nextQuery)).thenReturn(cursor.getValue());
        when(codec.encode(any(), eq(ProfileType.TEACHER), eq(nextQuery))).thenReturn("next-next");
        var next = service.searchTeachers(nextQuery);

        assertEquals(IntStream.rangeClosed(23, 42).mapToObj(ProfileReadFixtures::id).toList(),
                next.items().stream().map(card -> card.userId()).toList());
        assertEquals(20, next.items().size());
        assertEquals("next-next", next.nextCursor());
        assertThrows(UnsupportedOperationException.class, first.items()::clear);
        verify(identity, times(3)).findActiveUserIds(any());
        verify(identity, times(3)).findAgesByIds(any(), eq(TODAY));
        verifyNoInteractions(students);
    }

    @Test
    void studentSearchReturnsShortCardAndKeepsAgeDateFromCursor() {
        LocalDate originalDate = TODAY.minusDays(1);
        PublicProfileSearchQuery query = new PublicProfileSearchQuery(null, null, null, "previous");
        PublicProfilePageCursor after = new PublicProfilePageCursor(NOW, id(1), originalDate);
        when(codec.decode("previous", ProfileType.STUDENT, query)).thenReturn(after);
        when(students.findSearchCandidates(null, after, SEARCH_FETCH_LIMIT)).thenReturn(List.of(candidate(2)));
        when(identity.findActiveUserIds(Set.of(id(2)))).thenReturn(Set.of(id(2)));
        when(identity.findAgesByIds(Set.of(id(2)), originalDate)).thenReturn(Map.of(id(2), 19));
        when(students.findByUserIds(Set.of(id(2)))).thenReturn(Map.of(id(2), student(id(2))));

        var page = service.searchStudents(query);

        assertEquals(19, page.items().getFirst().age());
        assertEquals("Student", page.items().getFirst().displayName());
        assertEquals(Set.of(), page.items().getFirst().subjectCodes());
        assertNull(page.items().getFirst().photoUrl());
        assertNull(page.nextCursor());
        verify(identity).findAgesByIds(Set.of(id(2)), originalDate);
        verifyNoInteractions(teachers, subjects);
    }

    @Test
    void emptyRepositoryPageReturnsEmptyWithoutIdentityLookup() {
        var page = service.searchTeachers(new PublicProfileSearchQuery(null, null, null, null));
        assertTrue(page.items().isEmpty());
        assertNull(page.nextCursor());
        verifyNoInteractions(identity, codec, subjects);
        verify(teachers, never()).findByUserIds(any());
        verifyNoInteractions(students);
    }

    @Test
    void inactiveCandidatesAreSkippedAndAnEmptyFinalPageHasNoCursor() {
        List<PublicProfileSearchCandidate> candidates = IntStream.rangeClosed(1, SEARCH_FETCH_LIMIT)
                .mapToObj(this::candidate).toList();
        when(teachers.findSearchCandidates(isNull(), any(), eq(SEARCH_FETCH_LIMIT)))
                .thenAnswer(call -> candidatesAfter(candidates, call.getArgument(1)));
        when(identity.findActiveUserIds(any())).thenReturn(Set.of());

        var page = service.searchTeachers(new PublicProfileSearchQuery(null, null, null, null));

        assertTrue(page.items().isEmpty());
        assertNull(page.nextCursor());
        verify(teachers, times(2)).findSearchCandidates(isNull(), any(), eq(SEARCH_FETCH_LIMIT));
        verify(identity).findActiveUserIds(any());
        verify(identity, never()).findAgesByIds(any(), any());
        verify(teachers, never()).findByUserIds(any());
    }

    @Test
    void invalidCursorFailsBeforeRepositoryAndSubjectLookup() {
        PublicProfileSearchQuery query = new PublicProfileSearchQuery("MATH", null, null, "invalid");
        when(codec.decode("invalid", ProfileType.TEACHER, query)).thenThrow(new InvalidPublicProfileCursorException());
        assertThrows(InvalidPublicProfileCursorException.class, () -> service.searchTeachers(query));
        verifyNoInteractions(teachers, students, subjects, identity);
    }

    @Test
    void futureAgeDateInCursorIsRejected() {
        PublicProfileSearchQuery query = new PublicProfileSearchQuery(null, null, null, "future");
        when(codec.decode("future", ProfileType.TEACHER, query))
                .thenReturn(new PublicProfilePageCursor(NOW, id(1), TODAY.plusDays(1)));
        assertThrows(InvalidPublicProfileCursorException.class, () -> service.searchTeachers(query));
        verifyNoInteractions(teachers, students, subjects, identity);
    }

    @Test
    void unknownSubjectStopsSearch() {
        doThrow(new UnknownSubjectsException(Set.of("UNKNOWN"))).when(subjects).requireAllExist(Set.of("UNKNOWN"));
        assertThrows(UnknownSubjectsException.class,
                () -> service.searchTeachers(new PublicProfileSearchQuery("UNKNOWN", null, null, null)));
        verifyNoInteractions(teachers, students, identity);
    }

    @Test
    void missingAgeAndIdentityFailureAreNotSilentlyFilteredOut() {
        when(teachers.findSearchCandidates(isNull(), isNull(), eq(SEARCH_FETCH_LIMIT)))
                .thenReturn(List.of(candidate(1)));
        when(identity.findActiveUserIds(Set.of(id(1)))).thenReturn(Set.of(id(1)));
        PublicProfileSearchQuery query = new PublicProfileSearchQuery(null, null, null, null);
        assertThrows(ProfileReadConsistencyException.class, () -> service.searchTeachers(query));
        when(identity.findAgesByIds(Set.of(id(1)), TODAY)).thenThrow(new IllegalStateException("Identity unavailable"));
        assertThrows(IllegalStateException.class, () -> service.searchTeachers(query));
    }

    private PublicProfileSearchCandidate candidate(int number) {
        return new PublicProfileSearchCandidate(id(number), NOW.minusSeconds(number));
    }

    private List<PublicProfileSearchCandidate> candidatesAfter(
            List<PublicProfileSearchCandidate> candidates, PublicProfilePageCursor after) {

        return candidates.stream()
                .filter(candidate -> after == null
                        || candidate.createdAt().isBefore(after.createdAt())
                        || (candidate.createdAt().equals(after.createdAt())
                        && candidate.userId().compareTo(after.userId()) < 0))
                .limit(SEARCH_FETCH_LIMIT)
                .toList();
    }

    private Map<UUID, TeacherProfile> teacherProfiles(Set<UUID> ids) {
        Map<UUID, TeacherProfile> profiles = new HashMap<>();
        for (UUID userId : ids) {
            profiles.put(userId, teacher(userId));
        }
        return profiles;
    }
}
