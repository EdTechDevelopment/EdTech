package io.github.edtechdevelopment.identity.domain.user.event;

import io.github.edtechdevelopment.identity.domain.user.exception.InvalidUserDataException;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record UserAccountUpdatedDomainEvent(UUID userId, Set<String> changedFields, Instant occurredAt) implements UserDomainEvent {

    public UserAccountUpdatedDomainEvent {
        Objects.requireNonNull(userId, "User id must not be null");
        Objects.requireNonNull(occurredAt, "Event time must not be null");

        if (changedFields == null || changedFields.isEmpty()) {
            throw new InvalidUserDataException("Changed fields must not be empty");
        }
        if (changedFields.stream().anyMatch(Objects::isNull)) {
            throw new InvalidUserDataException("Changed fields must not contain null");
        }
        changedFields = Set.copyOf(changedFields);
    }
}
