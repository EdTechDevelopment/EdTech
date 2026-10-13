package io.github.edtechdevelopment.workflows.application.onboarding.model;

import io.github.edtechdevelopment.tutoring.api.model.profile.input.StudentProfileData;

import java.util.Objects;
import java.util.UUID;

public record AddStudentRoleCommand(UUID operationId, UUID actorUserId, StudentProfileData profile) {

    public AddStudentRoleCommand {
        Objects.requireNonNull(operationId, "Operation id must not be null");
        Objects.requireNonNull(actorUserId, "Actor user id must not be null");
        Objects.requireNonNull(profile, "Student profile must not be null");
    }
}

