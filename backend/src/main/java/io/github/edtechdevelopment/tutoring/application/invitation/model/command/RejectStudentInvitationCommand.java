package io.github.edtechdevelopment.tutoring.application.invitation.model.command;

import java.util.Objects;
import java.util.UUID;

public record RejectStudentInvitationCommand(UUID operationId, UUID invitationId, UUID studentUserId) {

    public RejectStudentInvitationCommand {
        Objects.requireNonNull(operationId, "Operation id must not be null");
        Objects.requireNonNull(invitationId, "Invitation id must not be null");
        Objects.requireNonNull(studentUserId, "Student user id must not be null");
    }
}
