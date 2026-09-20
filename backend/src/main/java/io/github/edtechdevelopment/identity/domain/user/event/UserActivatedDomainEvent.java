package io.github.edtechdevelopment.identity.domain.user.event;

import io.github.edtechdevelopment.identity.domain.user.model.Email;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record UserActivatedDomainEvent(UUID userId, Email email, Instant occurredAt) implements UserDomainEvent {

    public UserActivatedDomainEvent {
        Objects.requireNonNull(userId, "User id must not be null");
        Objects.requireNonNull(email, "Email must not be null");
        Objects.requireNonNull(occurredAt, "Event time must not be null");
    }
}
