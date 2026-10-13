package io.github.edtechdevelopment.tutoring.domain.invitation.exception;

import java.time.Instant;
import java.util.UUID;

public final class InvitationExpiredException extends RuntimeException {

    private final UUID invitationId;
    private final Instant expiresAt;

    public InvitationExpiredException(UUID invitationId, Instant expiresAt) {
        super("Invitation " + invitationId + " expired at " + expiresAt);
        this.invitationId = invitationId;
        this.expiresAt = expiresAt;
    }

    public UUID invitationId() {
        return invitationId;
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
