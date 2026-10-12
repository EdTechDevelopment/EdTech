package io.github.edtechdevelopment.tutoring.application.invitation.exception;

public final class InvitationCreationConflictException extends RuntimeException {

    private final Reason reason;

    public InvitationCreationConflictException(Reason reason) {
        super("Invitation cannot be created for this recipient");
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        SELF_INVITATION,
        ALREADY_PENDING,
        ALREADY_LINKED
    }
}
