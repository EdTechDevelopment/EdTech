package io.github.edtechdevelopment.tutoring.application.invitation.model.result;


import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SentInvitationResult(
        UUID invitationId,
        String studentEmail,
        String status,
        Instant createdAt,
        Instant expiresAt,
        Instant respondedAt
) {

    public SentInvitationResult {
        Objects.requireNonNull(invitationId, "Invitation id must not be null");
        Objects.requireNonNull(studentEmail, "Student email must not be null");
        Objects.requireNonNull(status, "Invitation status must not be null");
        Objects.requireNonNull(createdAt, "Creation time must not be null");
        Objects.requireNonNull(expiresAt, "Expiration time must not be null");
    }
}
