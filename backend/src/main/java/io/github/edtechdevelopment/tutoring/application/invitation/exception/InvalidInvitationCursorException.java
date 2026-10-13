package io.github.edtechdevelopment.tutoring.application.invitation.exception;

public final class InvalidInvitationCursorException extends RuntimeException {

    public InvalidInvitationCursorException() {
        super("Invitation cursor is invalid");
    }
}
