package io.github.edtechdevelopment.identity.application.mapper;

import io.github.edtechdevelopment.identity.api.event.AccountEmailVerifiedEvent;
import io.github.edtechdevelopment.identity.api.event.UserAccountUpdatedEvent;
import io.github.edtechdevelopment.identity.api.event.UserActivatedEvent;
import io.github.edtechdevelopment.identity.api.event.UserRegisteredEvent;
import io.github.edtechdevelopment.identity.api.model.AccountEmailVerificationPurpose;
import io.github.edtechdevelopment.identity.api.model.UserRoleView;
import io.github.edtechdevelopment.identity.api.model.UserStatusView;
import io.github.edtechdevelopment.identity.api.model.UserSummary;
import io.github.edtechdevelopment.identity.domain.user.event.UserAccountUpdatedDomainEvent;
import io.github.edtechdevelopment.identity.domain.user.event.UserActivatedDomainEvent;
import io.github.edtechdevelopment.identity.domain.user.event.UserRegisteredDomainEvent;
import io.github.edtechdevelopment.identity.domain.user.model.User;
import io.github.edtechdevelopment.identity.domain.verification.model.EmailVerification;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public final class IdentityApiMapper {

    public UserSummary toUserSummary(User user) {
        Objects.requireNonNull(user, "User must not be null");

        return new UserSummary(
                user.id(),
                user.email().value(),
                user.firstName(),
                user.lastName(),
                user.roles().stream()
                        .map(role -> UserRoleView.valueOf(role.name()))
                        .collect(Collectors.toUnmodifiableSet()),
                UserStatusView.valueOf(user.status().name())
        );
    }

    public AccountEmailVerifiedEvent toIntegrationEvent(
            EmailVerification verification,
            Instant occurredAt
    ) {
        Objects.requireNonNull(verification, "Email verification must not be null");
        Objects.requireNonNull(occurredAt, "Event time must not be null");

        return new AccountEmailVerifiedEvent(
                UUID.randomUUID(),
                verification.userId(),
                verification.targetEmail().value(),
                AccountEmailVerificationPurpose.valueOf(verification.purpose().name()),
                occurredAt
        );
    }

    public UserActivatedEvent toIntegrationEvent(UserActivatedDomainEvent domainEvent) {
        Objects.requireNonNull(domainEvent, "Domain event must not be null");

        return new UserActivatedEvent(
                UUID.randomUUID(),
                domainEvent.userId(),
                domainEvent.email().value(),
                domainEvent.occurredAt()
        );
    }

    public UserAccountUpdatedEvent toIntegrationEvent(UserAccountUpdatedDomainEvent domainEvent) {
        Objects.requireNonNull(domainEvent, "Domain event must not be null");

        return new UserAccountUpdatedEvent(
                UUID.randomUUID(),
                domainEvent.userId(),
                domainEvent.changedFields(),
                domainEvent.occurredAt()
        );
    }

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
