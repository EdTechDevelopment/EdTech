package io.github.edtechdevelopment.identity.api.event;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record UserAccountUpdatedEvent(
        UUID eventId,
        UUID userId,
        Set<String> changedFields,
        Instant occurredAt
) {

    public UserAccountUpdatedEvent {
        Objects.requireNonNull(eventId, "Event id must not be null");
        Objects.requireNonNull(userId, "User id must not be null");
        Objects.requireNonNull(changedFields, "Changed fields must not be null");
        Objects.requireNonNull(occurredAt, "Event time must not be null");

        if (changedFields.isEmpty()) {
            throw new IllegalArgumentException("Changed fields must not be empty");
        }
        if (changedFields.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Changed fields must not contain null");
        }

        changedFields = Set.copyOf(changedFields);
    }
}
