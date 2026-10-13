package io.github.edtechdevelopment.tutoring.application.invitation.model.result;


import java.util.Objects;
import java.util.UUID;

public record InvitationRejectedResult(UUID invitationId, String status) {

    public InvitationRejectedResult {
        Objects.requireNonNull(invitationId, "Invitation id must not be null");
        Objects.requireNonNull(status, "Invitation status must not be null");
        if (!status.equals("REJECTED")) {
            throw new IllegalArgumentException("Rejected result must have REJECTED status");
        }
    }
}
