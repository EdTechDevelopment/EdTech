package io.github.edtechdevelopment.tutoring.application.invitation.exception;

import java.util.UUID;

public final class InvitationResponseConflictException extends RuntimeException {

    private final UUID invitationId;
    private final Reason reason;

    public InvitationResponseConflictException(UUID invitationId, Reason reason) {
        super("Invitation cannot be answered in its current state");
        this.invitationId = invitationId;
        this.reason = reason;
    }

    public UUID invitationId() {
        return invitationId;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        EXPIRED,
        ALREADY_RESPONDED,
        STUDENT_PROFILE_REQUIRED,
        ALREADY_LINKED
    }
}
