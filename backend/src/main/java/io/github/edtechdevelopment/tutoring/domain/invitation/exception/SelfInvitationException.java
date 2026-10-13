package io.github.edtechdevelopment.tutoring.domain.invitation.exception;

public final class SelfInvitationException extends RuntimeException {

    public SelfInvitationException(String message) {
        super(message);
    }
}
