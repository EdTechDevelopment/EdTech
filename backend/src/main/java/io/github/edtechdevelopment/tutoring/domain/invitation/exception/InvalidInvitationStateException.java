package io.github.edtechdevelopment.tutoring.domain.invitation.exception;

import io.github.edtechdevelopment.tutoring.domain.invitation.model.InvitationStatus;

import java.util.UUID;

public final class InvalidInvitationStateException extends RuntimeException {

    private final UUID invitationId;
    private final InvitationStatus status;
    private final String operation;

    public InvalidInvitationStateException(UUID invitationId, InvitationStatus status, String operation) {
        super("Cannot " + operation + " invitation " + invitationId + " in status " + status);
        this.invitationId = invitationId;
        this.status = status;
        this.operation = operation;
    }

    public UUID invitationId() {
        return invitationId;
    }

    public InvitationStatus status() {
        return status;
    }

    public String operation() {
        return operation;
    }
}
