package io.github.edtechdevelopment.tutoring.application.invitation.model.query;

import io.github.edtechdevelopment.tutoring.domain.invitation.model.InvitationStatus;
import io.github.edtechdevelopment.tutoring.domain.invitation.model.StudentInvitation;

import java.util.Objects;

public record InvitationPageRow(StudentInvitation invitation, InvitationStatus statusAtAsOf) {

    public InvitationPageRow {
        Objects.requireNonNull(invitation, "Invitation must not be null");
        Objects.requireNonNull(statusAtAsOf, "Invitation status must not be null");
    }
}
