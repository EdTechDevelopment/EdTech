package io.github.edtechdevelopment.tutoring.application.invitation.model.command;

import java.util.Objects;
import java.util.UUID;

public record AcceptStudentInvitationCommand(UUID operationId, UUID invitationId, UUID studentUserId) {

    public AcceptStudentInvitationCommand {
        Objects.requireNonNull(operationId, "Operation id must not be null");
        Objects.requireNonNull(invitationId, "Invitation id must not be null");
        Objects.requireNonNull(studentUserId, "Student user id must not be null");
    }
}
