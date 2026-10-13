package io.github.edtechdevelopment.tutoring.application.profile.service;

import io.github.edtechdevelopment.tutoring.api.command.profile.CreateInitialProfilesCommand;
import io.github.edtechdevelopment.tutoring.api.command.profile.CreateStudentProfileCommand;
import io.github.edtechdevelopment.tutoring.api.command.profile.CreateTeacherProfileCommand;
import io.github.edtechdevelopment.tutoring.api.command.profile.InitialProfilesCreatedResult;
import io.github.edtechdevelopment.tutoring.api.command.profile.ProfileCreatedResult;
import io.github.edtechdevelopment.tutoring.api.exception.InvalidProfileDataException;
import io.github.edtechdevelopment.tutoring.api.exception.ProfileAlreadyExistsException;
import io.github.edtechdevelopment.tutoring.api.exception.UnknownSubjectsException;
import io.github.edtechdevelopment.tutoring.api.model.profile.ProfileTypeView;
import io.github.edtechdevelopment.tutoring.api.model.profile.input.StudentProfileData;
import io.github.edtechdevelopment.tutoring.api.model.profile.input.TeacherProfileData;
import io.github.edtechdevelopment.tutoring.api.query.TutoringSubjectQuery;
import io.github.edtechdevelopment.tutoring.application.integration.identity.exception.IdentityUserConsistencyException;
import io.github.edtechdevelopment.tutoring.application.integration.identity.port.out.IdentityAccountGateway;
import io.github.edtechdevelopment.tutoring.application.profile.mapper.ProfileInputMapper;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.StudentProfileRepository;
import io.github.edtechdevelopment.tutoring.application.profile.port.out.TeacherProfileRepository;
import io.github.edtechdevelopment.tutoring.domain.profile.model.ProfileType;
import io.github.edtechdevelopment.tutoring.domain.profile.model.StudentProfile;
import io.github.edtechdevelopment.tutoring.domain.profile.model.TeacherProfile;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RegistrationProfileServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final TeacherProfileRepository teacherRepository = mock(TeacherProfileRepository.class);
    private final StudentProfileRepository studentRepository = mock(StudentProfileRepository.class);
    private final IdentityAccountGateway identityGateway = mock(IdentityAccountGateway.class);
    private final TutoringSubjectQuery subjectQuery = mock(TutoringSubjectQuery.class);
    private final RegistrationProfileService service = new RegistrationProfileService(
            teacherRepository, studentRepository, identityGateway, subjectQuery, new ProfileInputMapper(),
            ProfileReadFixtures.CLOCK);

    @Test
    void createsBothProfilesWithIndependentDataAfterOneSubjectCheck() {
        InitialProfilesCreatedResult result = service.createInitialProfiles(new CreateInitialProfilesCommand(
                userId, Optional.of(teacherData()), Optional.of(studentData(Set.of("MATH", "PHYSICS")))));

        assertEquals(userId, result.userId());
        assertEquals(Set.of(ProfileTypeView.TEACHER, ProfileTypeView.STUDENT), result.createdProfiles());
        ArgumentCaptor<TeacherProfile> teacher = ArgumentCaptor.forClass(TeacherProfile.class);
        ArgumentCaptor<StudentProfile> student = ArgumentCaptor.forClass(StudentProfile.class);
        verify(teacherRepository).insert(teacher.capture());
        verify(studentRepository).insert(student.capture());
        assertEquals(userId, teacher.getValue().userId());
        assertEquals(userId, student.getValue().userId());
        assertEquals(ProfileReadFixtures.NOW, teacher.getValue().createdAt());
        assertEquals(teacher.getValue().createdAt(), student.getValue().createdAt());
        assertEquals("Teacher", teacher.getValue().displayName());
        assertEquals("Student", student.getValue().displayName());
        assertEquals("teacher@example.com", teacher.getValue().contactEmail().value());
        assertEquals("student@example.com", student.getValue().contactEmail().value());
        verify(identityGateway).requireRole(userId, ProfileType.TEACHER);
        verify(identityGateway).requireRole(userId, ProfileType.STUDENT);
        InOrder order = inOrder(subjectQuery, teacherRepository, studentRepository);
        order.verify(subjectQuery).requireAllExist(Set.of("MATH", "PHYSICS"));
        order.verify(teacherRepository).insert(any());
        order.verify(studentRepository).insert(any());
    }

    @Test
    void createsTeacherForRoleOnboarding() {
        ProfileCreatedResult result = service.createTeacherProfile(new CreateTeacherProfileCommand(userId, teacherData()));

        assertEquals(new ProfileCreatedResult(userId, ProfileTypeView.TEACHER), result);
        verify(identityGateway).requireRole(userId, ProfileType.TEACHER);
        verify(subjectQuery).requireAllExist(Set.of("MATH"));
        verify(teacherRepository).insert(any());
        verifyNoInteractions(studentRepository);
    }

    @Test
    void createsStudentWithoutSubjectsWithoutConsultingCatalog() {
        ProfileCreatedResult result = service.createStudentProfile(
                new CreateStudentProfileCommand(userId, studentData(Set.of())));

        assertEquals(new ProfileCreatedResult(userId, ProfileTypeView.STUDENT), result);
        verify(identityGateway).requireRole(userId, ProfileType.STUDENT);
        verify(studentRepository).insert(any());
        verifyNoInteractions(subjectQuery, teacherRepository);
    }

    @Test
    void rejectsInitialCommandWithoutProfiles() {
        InvalidProfileDataException error = assertThrows(InvalidProfileDataException.class,
                () -> service.createInitialProfiles(new CreateInitialProfilesCommand(
                        userId, Optional.empty(), Optional.empty())));

        assertEquals(Set.of("teacherProfile", "studentProfile"), error.invalidFields());
        verifyNoInteractions(identityGateway, subjectQuery, teacherRepository, studentRepository);
    }

    @Test
    void rejectsExistingTeacherProfileWithoutInsertingAgain() {
        when(teacherRepository.existsByUserId(userId)).thenReturn(true);

        ProfileAlreadyExistsException error = assertThrows(ProfileAlreadyExistsException.class,
                () -> service.createTeacherProfile(new CreateTeacherProfileCommand(userId, teacherData())));

        assertEquals(ProfileTypeView.TEACHER, error.profileType());
        assertEquals(userId, error.userId());
        verify(teacherRepository, never()).insert(any());
        verifyNoInteractions(subjectQuery, studentRepository);
    }

    @Test
    void rejectsExistingStudentBeforeWritingEitherInitialProfile() {
        when(studentRepository.existsByUserId(userId)).thenReturn(true);

        assertThrows(ProfileAlreadyExistsException.class,
                () -> service.createInitialProfiles(new CreateInitialProfilesCommand(
                        userId, Optional.of(teacherData()), Optional.of(studentData(Set.of())))));

        verify(teacherRepository, never()).insert(any());
        verify(studentRepository, never()).insert(any());
        verifyNoInteractions(subjectQuery);
    }

    @Test
    void missingRoleStopsCreationBeforeRepositoryAccess() {
        doThrow(new IdentityUserConsistencyException(userId, ProfileType.TEACHER))
                .when(identityGateway).requireRole(userId, ProfileType.TEACHER);

        assertThrows(IdentityUserConsistencyException.class,
                () -> service.createTeacherProfile(new CreateTeacherProfileCommand(userId, teacherData())));

        verifyNoInteractions(teacherRepository, studentRepository, subjectQuery);
    }

    @Test
    void invalidSecondProfileDoesNotWriteFirstProfile() {
        StudentProfileData invalidStudent = new StudentProfileData(" ", "student@example.com", List.of(), Set.of(), null);

        InvalidProfileDataException error = assertThrows(InvalidProfileDataException.class,
                () -> service.createInitialProfiles(new CreateInitialProfilesCommand(
                        userId, Optional.of(teacherData()), Optional.of(invalidStudent))));

        assertEquals(ProfileTypeView.STUDENT, error.profileType());
        assertEquals(Set.of("displayName"), error.invalidFields());
        verify(teacherRepository, never()).insert(any());
        verify(studentRepository, never()).insert(any());
    }

    @Test
    void unknownSubjectsStopBothInserts() {
        doThrow(new UnknownSubjectsException(Set.of("PHYSICS")))
                .when(subjectQuery).requireAllExist(Set.of("MATH", "PHYSICS"));

        UnknownSubjectsException error = assertThrows(UnknownSubjectsException.class,
                () -> service.createInitialProfiles(new CreateInitialProfilesCommand(
                        userId, Optional.of(teacherData()), Optional.of(studentData(Set.of("PHYSICS"))))));

        assertEquals(Set.of("PHYSICS"), error.unknownCodes());
        verify(teacherRepository, never()).insert(any());
        verify(studentRepository, never()).insert(any());
    }

    private static TeacherProfileData teacherData() {
        return new TeacherProfileData(" Teacher ", " Teacher@Example.com ", List.of("@teacher"),
                Set.of("MATH"), "Description", "Education", 0, "City", null);
    }

    private static StudentProfileData studentData(Set<String> subjects) {
        return new StudentProfileData("Student", "student@example.com", List.of("@student"), subjects, null);
    }
}
