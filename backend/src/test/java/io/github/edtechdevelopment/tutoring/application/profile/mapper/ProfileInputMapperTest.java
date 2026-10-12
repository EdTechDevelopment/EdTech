package io.github.edtechdevelopment.tutoring.application.profile.mapper;

import io.github.edtechdevelopment.tutoring.api.exception.InvalidProfileDataException;
import io.github.edtechdevelopment.tutoring.api.model.profile.ProfileTypeView;
import io.github.edtechdevelopment.tutoring.api.model.profile.input.StudentProfileData;
import io.github.edtechdevelopment.tutoring.api.model.profile.input.TeacherProfileData;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfileInputMapperTest {

    private final ProfileInputMapper mapper = new ProfileInputMapper();

    @ParameterizedTest
    @MethodSource("invalidTeacherData")
    void translatesTeacherValidationToPublicFieldError(String field, TeacherProfileData data) {
        InvalidProfileDataException error = assertThrows(InvalidProfileDataException.class,
                () -> mapper.toTeacherProfile(UUID.randomUUID(), data, Instant.parse("2026-01-01T00:00:00Z")));

        assertEquals(ProfileTypeView.TEACHER, error.profileType());
        assertEquals(Set.of(field), error.invalidFields());
        assertNull(error.getCause());
    }

    @ParameterizedTest
    @MethodSource("invalidStudentData")
    void translatesStudentValidationToPublicFieldError(String field, StudentProfileData data) {
        InvalidProfileDataException error = assertThrows(InvalidProfileDataException.class,
                () -> mapper.toStudentProfile(UUID.randomUUID(), data, Instant.parse("2026-01-01T00:00:00Z")));

        assertEquals(ProfileTypeView.STUDENT, error.profileType());
        assertEquals(Set.of(field), error.invalidFields());
        assertNull(error.getCause());
    }

    private static Stream<Arguments> invalidTeacherData() {
        return Stream.of(
                Arguments.of("displayName", teacherData(" ", "a@example.com", Set.of("MATH"), 0, null)),
                Arguments.of("contactEmail", teacherData("Teacher", "invalid", Set.of("MATH"), 0, null)),
                Arguments.of("subjectCodes", teacherData("Teacher", "a@example.com", Set.of(), 0, null)),
                Arguments.of("subjectCodes", teacherData("Teacher", "a@example.com", Set.of("math"), 0, null)),
                Arguments.of("experienceYears", teacherData("Teacher", "a@example.com", Set.of("MATH"), -1, null)),
                Arguments.of("photoUrl", teacherData("Teacher", "a@example.com", Set.of("MATH"), 0, "https://invalid host"))
        );
    }

    private static Stream<Arguments> invalidStudentData() {
        return Stream.of(
                Arguments.of("displayName", new StudentProfileData(" ", "a@example.com", List.of(), Set.of(), null)),
                Arguments.of("contactEmail", new StudentProfileData("Student", "invalid", List.of(), Set.of(), null)),
                Arguments.of("subjectCodes", new StudentProfileData("Student", "a@example.com", List.of(), Set.of("math"), null)),
                Arguments.of("photoUrl", new StudentProfileData("Student", "a@example.com", List.of(), Set.of(), "https://invalid host"))
        );
    }

    private static TeacherProfileData teacherData(
            String name, String email, Set<String> subjects, Integer experience, String photo) {
        return new TeacherProfileData(name, email, List.of(), subjects, null, null, experience, null, photo);
    }
}
