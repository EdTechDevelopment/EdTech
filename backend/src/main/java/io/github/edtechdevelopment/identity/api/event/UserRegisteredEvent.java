package io.github.edtechdevelopment.identity.api.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record UserRegisteredEvent(
        UUID eventId,
        UUID userId,
        String email,
        Instant occurredAt
) {

    public UserRegisteredEvent {
        Objects.requireNonNull(eventId, "Event id must not be null");
        Objects.requireNonNull(userId, "User id must not be null");
        Objects.requireNonNull(email, "Email must not be null");
        Objects.requireNonNull(occurredAt, "Event time must not be null");

        if (email.isBlank()) {
            throw new IllegalArgumentException("Email must not be blank");
        }
    }
}
