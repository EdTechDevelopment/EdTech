package io.github.edtechdevelopment.workflows.application.onboarding.model;

import io.github.edtechdevelopment.tutoring.api.model.profile.input.TeacherProfileData;

import java.util.Objects;
import java.util.UUID;

public record AddTeacherRoleCommand(UUID operationId, UUID actorUserId, TeacherProfileData profile) {

    public AddTeacherRoleCommand {
        Objects.requireNonNull(operationId, "Operation id must not be null");
        Objects.requireNonNull(actorUserId, "Actor user id must not be null");
        Objects.requireNonNull(profile, "Teacher profile must not be null");
    }
}

