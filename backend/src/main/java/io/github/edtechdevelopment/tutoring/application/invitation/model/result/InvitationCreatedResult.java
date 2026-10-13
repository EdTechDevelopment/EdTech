package io.github.edtechdevelopment.tutoring.application.invitation.model.result;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record InvitationCreatedResult(UUID invitationId, Instant expiresAt) {

    public InvitationCreatedResult {
        Objects.requireNonNull(invitationId, "Invitation id must not be null");
        Objects.requireNonNull(expiresAt, "Expiration time must not be null");
    }
}
