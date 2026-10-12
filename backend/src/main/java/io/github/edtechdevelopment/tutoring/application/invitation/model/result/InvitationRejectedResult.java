package io.github.edtechdevelopment.tutoring.application.invitation.model.result;

import io.github.edtechdevelopment.tutoring.domain.invitation.model.InvitationStatus;

import java.util.Objects;
import java.util.UUID;

public record InvitationRejectedResult(UUID invitationId, InvitationStatus status) {

    public InvitationRejectedResult {
        Objects.requireNonNull(invitationId, "Invitation id must not be null");
        Objects.requireNonNull(status, "Invitation status must not be null");
        if (status != InvitationStatus.REJECTED) {
            throw new IllegalArgumentException("Rejected result must have REJECTED status");
        }
    }
}
