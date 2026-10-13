package io.github.edtechdevelopment.tutoring.api.command.profile;

import io.github.edtechdevelopment.tutoring.api.model.profile.input.TeacherProfileData;

import java.util.Objects;
import java.util.UUID;

public record CreateTeacherProfileCommand(UUID userId, TeacherProfileData profile) {

    public CreateTeacherProfileCommand {
        Objects.requireNonNull(userId, "User id must not be null");
        Objects.requireNonNull(profile, "Teacher profile data must not be null");
    }
}
