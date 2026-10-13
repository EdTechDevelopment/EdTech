package io.github.edtechdevelopment.tutoring.application.invitation.model.result;

import io.github.edtechdevelopment.tutoring.api.model.profile.summary.TeacherProfileSummary;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record IncomingInvitationResult(
        UUID invitationId,
        TeacherProfileSummary teacher,
        String status,
        Instant createdAt,
        Instant expiresAt,
        Instant respondedAt
) {

    public IncomingInvitationResult {
        Objects.requireNonNull(invitationId, "Invitation id must not be null");
        Objects.requireNonNull(teacher, "Teacher summary must not be null");
        Objects.requireNonNull(status, "Invitation status must not be null");
        Objects.requireNonNull(createdAt, "Creation time must not be null");
        Objects.requireNonNull(expiresAt, "Expiration time must not be null");
    }
}
