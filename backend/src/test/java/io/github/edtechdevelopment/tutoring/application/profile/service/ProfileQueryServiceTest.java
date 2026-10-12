package io.github.edtechdevelopment.tutoring.application.profile.service;

import io.github.edtechdevelopment.tutoring.api.exception.ProfileNotFoundException;
import io.github.edtechdevelopment.tutoring.api.model.profile.summary.StudentProfileSummary;
import io.github.edtechdevelopment.tutoring.api.model.profile.summary.TeacherProfileSummary;
import io.github.edtechdevelopment.tutoring.application.integration.identity.port.out.IdentityAccountGateway;
import io.github.edtechdevelopment.tutoring.application.profile.exception.ProfileReadConsistencyException;
import io.github.edtechdevelopment.tutoring.application.profile.mapper.ProfileViewMapper;
import io.github.edtechdevelopment.tutoring.application.profile.model.query.ProfileNameData;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.StudentProfileRepository;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.TeacherProfileRepository;
import io.github.edtechdevelopment.tutoring.application.relationship.port.out.TeacherStudentRepository;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static io.github.edtechdevelopment.tutoring.application.profile.service.ProfileReadFixtures.CLOCK;
import static io.github.edtechdevelopment.tutoring.application.profile.service.ProfileReadFixtures.TODAY;
import static io.github.edtechdevelopment.tutoring.application.profile.service.ProfileReadFixtures.id;
import static io.github.edtechdevelopment.tutoring.application.profile.service.ProfileReadFixtures.student;
import static io.github.edtechdevelopment.tutoring.application.profile.service.ProfileReadFixtures.teacher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProfileQueryServiceTest {

    private final TeacherProfileRepository teachers = mock(TeacherProfileRepository.class);
    private final StudentProfileRepository students = mock(StudentProfileRepository.class);
    private final TeacherStudentRepository relationships = mock(TeacherStudentRepository.class);
    private final IdentityAccountGateway identity = mock(IdentityAccountGateway.class);
    private final ProfileQueryService service = new ProfileQueryService(
            teachers, students, relationships, identity, new ProfileViewMapper(), CLOCK);

    @Test
    void selfViewsUseAccountAgeAtUtcDateWithoutRequiringActiveStatus() {
        UUID userId = id(1);
        when(teachers.findByUserId(userId)).thenReturn(Optional.of(teacher(userId)));
        when(students.findByUserId(userId)).thenReturn(Optional.of(student(userId)));
        when(identity.findAgesByIds(Set.of(userId), TODAY)).thenReturn(Map.of(userId, 29));

        var teacherView = service.findTeacherProfile(userId).orElseThrow();
        var studentView = service.findStudentProfile(userId).orElseThrow();

        assertEquals(29, teacherView.age());
        assertEquals(29, studentView.age());
        assertEquals("contact@example.com", teacherView.contactEmail());
        assertEquals("student@example.com", studentView.contactEmail());
        assertEquals(Set.of("MATH"), teacherView.subjectCodes());
        assertEquals(0, teacherView.experienceYears());
        assertEquals("https://example.com/photo.png", teacherView.photoUrl());
        assertNull(studentView.photoUrl());
        verify(identity, never()).findActiveUserIds(any());
    }

    @Test
    void missingProfilesReturnEmptyWithoutIdentityRequests() {
        assertTrue(service.findTeacherProfile(id(1)).isEmpty());
        assertTrue(service.findStudentProfile(id(1)).isEmpty());
        assertTrue(service.findPublicTeacherProfile(id(1)).isEmpty());
        assertTrue(service.findPublicStudentProfile(id(1)).isEmpty());
        verifyNoInteractions(identity, relationships);
    }

    @Test
    void publicTeacherRequiresActiveAccountAndContainsProfileContacts() {
        UUID userId = id(1);
        when(teachers.findByUserId(userId)).thenReturn(Optional.of(teacher(userId)));
        when(identity.findActiveUserIds(Set.of(userId))).thenReturn(Set.of(userId));
        when(identity.findAgesByIds(Set.of(userId), TODAY)).thenReturn(Map.of(userId, 29));

        var view = service.findPublicTeacherProfile(userId).orElseThrow();

        assertEquals(userId, view.userId());
        assertEquals("Description", view.description());
        assertEquals("Education", view.education());
        assertEquals("City", view.city());
        assertEquals("contact@example.com", view.contactEmail());
        verifyNoInteractions(relationships);
    }

    @Test
    void inactiveTeacherAndStudentAreHiddenWithoutAgeLookup() {
        when(teachers.findByUserId(id(1))).thenReturn(Optional.of(teacher(id(1))));
        when(students.findByUserId(id(2))).thenReturn(Optional.of(student(id(2))));

        assertTrue(service.findPublicTeacherProfile(id(1)).isEmpty());
        assertTrue(service.findPublicStudentProfile(id(2)).isEmpty());
        verify(identity, never()).findAgesByIds(any(), any());
    }

    @Test
    void missingAgeIsAConsistencyErrorAndTechnicalFailureIsNotHidden() {
        UUID userId = id(1);
        when(teachers.findByUserId(userId)).thenReturn(Optional.of(teacher(userId)));
        assertThrows(ProfileReadConsistencyException.class, () -> service.findTeacherProfile(userId));
        when(identity.findActiveUserIds(Set.of(userId))).thenThrow(new IllegalStateException("Identity unavailable"));
        assertThrows(IllegalStateException.class, () -> service.findPublicTeacherProfile(userId));
    }

    @Test
    void summaryReadsReturnOnlyFoundIdsInImmutableMapsWithoutIdentityCalls() {
        Set<UUID> ids = Set.of(id(1), id(2));
        when(teachers.findSummariesByUserIds(ids)).thenReturn(Map.of(id(1), new ProfileNameData(id(1), "Teacher")));
        when(students.findSummariesByUserIds(ids)).thenReturn(Map.of(id(2), new ProfileNameData(id(2), "Student")));

        var teacherSummaries = service.findTeacherSummaries(ids);
        var studentSummaries = service.findStudentSummaries(ids);

        assertEquals(Map.of(id(1), new TeacherProfileSummary(id(1), "Teacher")), teacherSummaries);
        assertEquals(Map.of(id(2), new StudentProfileSummary(id(2), "Student")), studentSummaries);
        assertThrows(UnsupportedOperationException.class, teacherSummaries::clear);
        verify(teachers).findSummariesByUserIds(ids);
        verify(students).findSummariesByUserIds(ids);
        verifyNoInteractions(identity);
    }

    @Test
    void emptyAndInvalidSummaryBatchesDoNotReachRepositories() {
        assertEquals(Map.of(), service.findTeacherSummaries(Set.of()));
        assertEquals(Map.of(), service.findStudentSummaries(Set.of()));
        Set<UUID> withNull = new HashSet<>();
        withNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> service.findTeacherSummaries(withNull));
        assertThrows(IllegalArgumentException.class, () -> service.findStudentSummaries(null));
        verifyNoInteractions(teachers, students, identity);
    }

    @Test
    void teacherExistenceCheckDoesNotInspectSubjectsOrIdentity() {
        when(teachers.existsByUserId(id(1))).thenReturn(true);
        service.requireTeacherProfile(id(1));
        assertThrows(ProfileNotFoundException.class, () -> service.requireTeacherProfile(id(2)));
        verifyNoInteractions(identity, students, relationships);
    }

    @Test
    void noLinkReturnsEmptyBeforeProfileLookup() {
        assertTrue(service.findLinkedStudentProfile(id(1), id(2)).isEmpty());
        assertTrue(service.findLinkedTeacherProfile(id(2), id(1)).isEmpty());
        verify(relationships, times(2)).exists(id(1), id(2));
        verifyNoInteractions(teachers, students, identity);
    }

    @Test
    void linkedStudentReturnsPublicViewAfterCheckingLinkAndActivity() {
        when(relationships.exists(id(1), id(2))).thenReturn(true);
        when(students.findByUserId(id(2))).thenReturn(Optional.of(student(id(2))));
        when(identity.findActiveUserIds(Set.of(id(2)))).thenReturn(Set.of(id(2)));
        when(identity.findAgesByIds(Set.of(id(2)), TODAY)).thenReturn(Map.of(id(2), 19));

        assertEquals(19, service.findLinkedStudentProfile(id(1), id(2)).orElseThrow().age());
        var order = inOrder(relationships, students, identity);
        order.verify(relationships).exists(id(1), id(2));
        order.verify(students).findByUserId(id(2));
        order.verify(identity).findActiveUserIds(Set.of(id(2)));
        order.verify(identity).findAgesByIds(Set.of(id(2)), TODAY);
    }

    @Test
    void linkedTeacherIsHiddenWhenInactiveAndMissingLinkedProfilesAreErrors() {
        when(relationships.exists(id(1), id(2))).thenReturn(true);
        when(teachers.findByUserId(id(1))).thenReturn(Optional.of(teacher(id(1))));
        assertTrue(service.findLinkedTeacherProfile(id(2), id(1)).isEmpty());
        assertThrows(ProfileReadConsistencyException.class, () -> service.findLinkedStudentProfile(id(1), id(2)));
        when(teachers.findByUserId(id(1))).thenReturn(Optional.empty());
        assertThrows(ProfileReadConsistencyException.class, () -> service.findLinkedTeacherProfile(id(2), id(1)));
    }
}
