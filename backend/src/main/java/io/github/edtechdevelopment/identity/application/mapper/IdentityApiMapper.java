package io.github.edtechdevelopment.identity.application.mapper;

import io.github.edtechdevelopment.identity.api.event.UserRegisteredEvent;
import io.github.edtechdevelopment.identity.domain.user.event.UserRegisteredDomainEvent;

import java.util.Objects;
import java.util.UUID;

public final class IdentityApiMapper {

    public UserRegisteredEvent toIntegrationEvent(UserRegisteredDomainEvent domainEvent) {
        Objects.requireNonNull(domainEvent, "Domain event must not be null");

        return new UserRegisteredEvent(
                UUID.randomUUID(),
                domainEvent.userId(),
                domainEvent.email().value(),
                domainEvent.occurredAt()
        );
    }
}
