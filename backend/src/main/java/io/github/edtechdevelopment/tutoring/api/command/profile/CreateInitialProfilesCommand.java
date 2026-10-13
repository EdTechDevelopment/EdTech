package io.github.edtechdevelopment.tutoring.api.command.profile;

import io.github.edtechdevelopment.tutoring.api.model.profile.input.StudentProfileData;
import io.github.edtechdevelopment.tutoring.api.model.profile.input.TeacherProfileData;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record CreateInitialProfilesCommand(
        UUID userId,
        Optional<TeacherProfileData> teacherProfile,
        Optional<StudentProfileData> studentProfile
) {

    public CreateInitialProfilesCommand {
        Objects.requireNonNull(userId, "User id must not be null");
        Objects.requireNonNull(teacherProfile, "Teacher profile option must not be null");
        Objects.requireNonNull(studentProfile, "Student profile option must not be null");
    }
}
