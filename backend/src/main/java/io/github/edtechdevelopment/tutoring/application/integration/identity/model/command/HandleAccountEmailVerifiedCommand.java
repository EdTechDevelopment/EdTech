package io.github.edtechdevelopment.tutoring.application.integration.identity.model.command;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record HandleAccountEmailVerifiedCommand(
        UUID eventId,
        UUID userId,
        String verifiedEmail,
        Instant verifiedAt
) {

    public HandleAccountEmailVerifiedCommand {
        Objects.requireNonNull(eventId, "Event id must not be null");
        Objects.requireNonNull(userId, "User id must not be null");
        Objects.requireNonNull(verifiedEmail, "Verified email must not be null");
        Objects.requireNonNull(verifiedAt, "Verification time must not be null");
        if (verifiedEmail.isBlank()) {
            throw new IllegalArgumentException("Verified email must not be blank");
        }
    }
}
