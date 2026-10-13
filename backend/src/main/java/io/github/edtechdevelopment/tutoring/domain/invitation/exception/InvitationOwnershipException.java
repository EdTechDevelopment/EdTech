package io.github.edtechdevelopment.tutoring.domain.invitation.exception;

import java.util.UUID;

public final class InvitationOwnershipException extends RuntimeException {

    private final UUID invitationId;

    public InvitationOwnershipException(UUID invitationId) {
        super("Invitation " + invitationId + " belongs to another student");
        this.invitationId = invitationId;
    }

    public UUID invitationId() {
        return invitationId;
    }
}
