package io.github.edtechdevelopment.tutoring.application.invitation.model.notification;

import io.github.edtechdevelopment.tutoring.domain.invitation.model.InvitationEmail;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record StudentInvitationNotification(
        UUID invitationId,
        InvitationEmail studentEmail,
        String teacherDisplayName,
        Instant expiresAt,
        String deduplicationKey
) {

    public StudentInvitationNotification {
        Objects.requireNonNull(invitationId, "Invitation id must not be null");
        Objects.requireNonNull(studentEmail, "Student email must not be null");
        Objects.requireNonNull(teacherDisplayName, "Teacher display name must not be null");
        Objects.requireNonNull(expiresAt, "Expiration time must not be null");
        Objects.requireNonNull(deduplicationKey, "Deduplication key must not be null");
    }

    @Override
    public String toString() {
        return "StudentInvitationNotification[PROTECTED]";
    }
}
