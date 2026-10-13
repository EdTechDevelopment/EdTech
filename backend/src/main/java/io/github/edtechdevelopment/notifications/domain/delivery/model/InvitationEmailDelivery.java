package io.github.edtechdevelopment.notifications.domain.delivery.model;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record InvitationEmailDelivery(UUID id, UUID invitationId, String recipientEmail, String teacherDisplayName,
                                     URI invitationUrl, Instant expiresAt, Instant createdAt, String deduplicationKey) {
    public InvitationEmailDelivery {
        Objects.requireNonNull(id); Objects.requireNonNull(invitationId);
        Objects.requireNonNull(recipientEmail); Objects.requireNonNull(teacherDisplayName);
        Objects.requireNonNull(invitationUrl); Objects.requireNonNull(expiresAt);
        Objects.requireNonNull(createdAt); Objects.requireNonNull(deduplicationKey);
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("Invitation delivery must not be expired");
        }
    }
    @Override
    public String toString() {
        return "InvitationEmailDelivery[PROTECTED]";
    }
}
