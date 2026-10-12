package io.github.edtechdevelopment.tutoring.application.profile.service;

import io.github.edtechdevelopment.tutoring.api.exception.InvalidProfileDataException;
import io.github.edtechdevelopment.tutoring.api.exception.ProfileNotFoundException;
import io.github.edtechdevelopment.tutoring.api.exception.UnknownSubjectsException;
import io.github.edtechdevelopment.tutoring.api.model.profile.ProfileTypeView;
import io.github.edtechdevelopment.tutoring.api.query.TutoringSubjectQuery;
import io.github.edtechdevelopment.tutoring.application.profile.mapper.ProfileInputMapper;
import io.github.edtechdevelopment.tutoring.application.profile.model.command.UpdateStudentProfileCommand;
import io.github.edtechdevelopment.tutoring.application.profile.model.command.UpdateTeacherProfileCommand;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.StudentProfileRepository;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.TeacherProfileRepository;
import io.github.edtechdevelopment.tutoring.domain.profile.model.ProfileEmail;
import io.github.edtechdevelopment.tutoring.domain.profile.model.StudentProfile;
import io.github.edtechdevelopment.tutoring.domain.profile.model.TeacherProfile;
import io.github.edtechdevelopment.tutoring.domain.subject.model.SubjectCode;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProfileUpdateServicesTest {

    private final UUID userId = UUID.randomUUID();
    private final TeacherProfileRepository teacherRepository = mock(TeacherProfileRepository.class);
    private final StudentProfileRepository studentRepository = mock(StudentProfileRepository.class);
    private final TutoringSubjectQuery subjectQuery = mock(TutoringSubjectQuery.class);
    private final ProfileInputMapper mapper = new ProfileInputMapper();
    private final TeacherProfileUpdateService teacherService = new TeacherProfileUpdateService(teacherRepository, subjectQuery, mapper);
    private final StudentProfileUpdateService studentService = new StudentProfileUpdateService(studentRepository, subjectQuery, mapper);

    @Test
    void updatesTeacherFieldsAndContactEmailTogetherAndClearsOptionalFields() {
        TeacherProfile profile = teacherProfile();
        when(teacherRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(profile));
        UpdateTeacherProfileCommand command = new UpdateTeacherProfileCommand(userId, " New teacher ",
                " New@Example.com ", List.of("@new"), Set.of("PHYSICS"), null, null, 0, null, null);

        teacherService.execute(command);

        assertEquals(userId, profile.userId());
        assertEquals(ProfileReadFixtures.NOW, profile.createdAt());
        assertEquals("New teacher", profile.displayName());
        assertEquals("new@example.com", profile.contactEmail().value());
        assertEquals(List.of("@new"), profile.contactDetails());
        assertEquals(Set.of(new SubjectCode("PHYSICS")), profile.subjectCodes());
        assertEquals(0, profile.experienceYears());
        assertNull(profile.description());
        assertNull(profile.education());
        assertNull(profile.city());
        assertNull(profile.photoUrl());
        InOrder order = inOrder(subjectQuery, teacherRepository);
        order.verify(subjectQuery).requireAllExist(Set.of("PHYSICS"));
        order.verify(teacherRepository).findByUserIdForUpdate(userId);
        order.verify(teacherRepository).update(profile);
        verifyNoInteractions(studentRepository);
    }

    @Test
    void studentCanClearAllSubjectsAndPhotoWhileChangingEmail() {
        StudentProfile profile = studentProfile();
        when(studentRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(profile));

        studentService.execute(new UpdateStudentProfileCommand(userId, "New student", "new@example.com",
                List.of(), Set.of(), null));

        assertEquals("New student", profile.displayName());
        assertEquals(ProfileReadFixtures.NOW, profile.createdAt());
        assertEquals("new@example.com", profile.contactEmail().value());
        assertEquals(Set.of(), profile.subjectCodes());
        assertEquals(List.of(), profile.contactDetails());
        assertNull(profile.photoUrl());
        verify(studentRepository).update(profile);
        verifyNoInteractions(subjectQuery, teacherRepository);
    }

    @Test
    void validatesNonemptyStudentSelectionAndPreservesItsPhoto() {
        StudentProfile profile = studentProfile();
        when(studentRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(profile));

        studentService.execute(new UpdateStudentProfileCommand(userId, "Student", "old@example.com",
                List.of(), Set.of("PHYSICS"), "https://example.com/photo.png"));

        assertEquals(Set.of(new SubjectCode("PHYSICS")), profile.subjectCodes());
        assertEquals(URI.create("https://example.com/photo.png"), profile.photoUrl());
        verify(subjectQuery).requireAllExist(Set.of("PHYSICS"));
        verify(studentRepository).update(profile);
    }

    @Test
    void unknownSubjectDoesNotLoadOrChangeExistingTeacher() {
        TeacherProfile profile = teacherProfile();
        doThrow(new UnknownSubjectsException(Set.of("PHYSICS")))
                .when(subjectQuery).requireAllExist(Set.of("PHYSICS"));

        assertThrows(UnknownSubjectsException.class, () -> teacherService.execute(new UpdateTeacherProfileCommand(
                userId, "New", "new@example.com", List.of(), Set.of("PHYSICS"), null, null, null, null, null)));

        assertEquals("Old teacher", profile.displayName());
        assertEquals("old@example.com", profile.contactEmail().value());
        verifyNoInteractions(teacherRepository, studentRepository);
    }

    @Test
    void invalidEmailDoesNotChangeExistingProfile() {
        StudentProfile profile = studentProfile();
        when(studentRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(profile));
        InvalidProfileDataException error = assertThrows(InvalidProfileDataException.class,
                () -> studentService.execute(new UpdateStudentProfileCommand(
                        userId, "Student", "invalid", List.of(), Set.of(), null)));

        assertEquals(ProfileTypeView.STUDENT, error.profileType());
        assertEquals(Set.of("contactEmail"), error.invalidFields());
        assertEquals("old@example.com", profile.contactEmail().value());
        verify(studentRepository).findByUserIdForUpdate(userId);
        verify(studentRepository, never()).update(any());
        verifyNoInteractions(teacherRepository, subjectQuery);
    }

    @Test
    void rejectsEmptyTeacherSelectionBeforeChangingOtherFields() {
        InvalidProfileDataException error = assertThrows(InvalidProfileDataException.class,
                () -> teacherService.execute(new UpdateTeacherProfileCommand(
                        userId, "New", "new@example.com", List.of(), Set.of(), null, null, null, null, null)));

        assertEquals(Set.of("subjectCodes"), error.invalidFields());
        verifyNoInteractions(teacherRepository, studentRepository, subjectQuery);
    }

    @Test
    void reportsMissingTeacherWithoutCreatingOne() {
        ProfileNotFoundException error = assertThrows(ProfileNotFoundException.class,
                () -> teacherService.execute(new UpdateTeacherProfileCommand(userId, "Teacher", "teacher@example.com",
                        List.of(), Set.of("MATH"), null, null, null, null, null)));

        assertEquals(userId, error.userId());
        assertEquals(ProfileTypeView.TEACHER, error.profileType());
        verify(teacherRepository, never()).update(any());
        verify(teacherRepository, never()).insert(any());
    }

    @Test
    void reportsMissingStudentWithoutCreatingOne() {
        ProfileNotFoundException error = assertThrows(ProfileNotFoundException.class,
                () -> studentService.execute(new UpdateStudentProfileCommand(
                        userId, "Student", "student@example.com", List.of(), Set.of(), null)));

        assertEquals(userId, error.userId());
        assertEquals(ProfileTypeView.STUDENT, error.profileType());
        verify(studentRepository, never()).update(any());
        verify(studentRepository, never()).insert(any());
    }

    private TeacherProfile teacherProfile() {
        return TeacherProfile.create(userId, "Old teacher", new ProfileEmail("old@example.com"), List.of("@old"),
                Set.of(new SubjectCode("MATH")), "Old description", "Old education", 5, "Old city",
                URI.create("https://example.com/old.png"), ProfileReadFixtures.NOW);
    }

    private StudentProfile studentProfile() {
        return StudentProfile.create(userId, "Old student", new ProfileEmail("old@example.com"), List.of(),
                Set.of(new SubjectCode("MATH")), URI.create("https://example.com/old.png"), ProfileReadFixtures.NOW);
    }
}
