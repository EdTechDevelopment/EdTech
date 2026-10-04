package io.github.edtechdevelopment.tutoring.api.command.profile;

import io.github.edtechdevelopment.tutoring.api.model.profile.input.StudentProfileData;

import java.util.Objects;
import java.util.UUID;

public record CreateStudentProfileCommand(UUID userId, StudentProfileData profile) {

    public CreateStudentProfileCommand {
        Objects.requireNonNull(userId, "User id must not be null");
        Objects.requireNonNull(profile, "Student profile data must not be null");
    }
}
