package io.github.edtechdevelopment.tutoring.application.invitation.exception;

import java.util.UUID;

public final class InvitationUnavailableException extends RuntimeException {

    public InvitationUnavailableException(UUID invitationId) {
        super("Invitation is unavailable");
    }
}
