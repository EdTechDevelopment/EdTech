package io.github.edtechdevelopment.notifications.api.command;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SendStudentInvitationCommand(UUID invitationId, String recipientEmail, String teacherDisplayName,
                                           Instant expiresAt, String deduplicationKey) {
    public SendStudentInvitationCommand {
        Objects.requireNonNull(invitationId, "Invitation id must not be null");
        Objects.requireNonNull(expiresAt, "Expiration time must not be null");
        if (recipientEmail == null || recipientEmail.isBlank() || teacherDisplayName == null || teacherDisplayName.isBlank()
                || deduplicationKey == null || deduplicationKey.isBlank()) {
            throw new IllegalArgumentException("Invitation notification data is incomplete");
        }
    }
    @Override
    public String toString() {
        return "SendStudentInvitationCommand[PROTECTED]";
    }
}
