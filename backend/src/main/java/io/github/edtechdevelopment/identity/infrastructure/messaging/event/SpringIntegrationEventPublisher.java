package io.github.edtechdevelopment.identity.infrastructure.messaging.event;

import io.github.edtechdevelopment.identity.api.event.AccountEmailVerifiedEvent;
import io.github.edtechdevelopment.identity.api.event.UserAccountUpdatedEvent;
import io.github.edtechdevelopment.identity.api.event.UserActivatedEvent;
import io.github.edtechdevelopment.identity.api.event.UserRegisteredEvent;
import io.github.edtechdevelopment.identity.application.port.out.messaging.IntegrationEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public final class SpringIntegrationEventPublisher implements IntegrationEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public SpringIntegrationEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "Application event publisher must not be null");
    }

    @Override
    public void publish(AccountEmailVerifiedEvent event) {
        eventPublisher.publishEvent(Objects.requireNonNull(event, "Account email verified event must not be null"));
    }

    @Override
    public void publish(UserRegisteredEvent event) {
        eventPublisher.publishEvent(Objects.requireNonNull(event, "User registered event must not be null"));
    }

    @Override
    public void publish(UserActivatedEvent event) {
        eventPublisher.publishEvent(Objects.requireNonNull(event, "User activated event must not be null"));
    }

    @Override
    public void publish(UserAccountUpdatedEvent event) {
        eventPublisher.publishEvent(Objects.requireNonNull(event, "User account updated event must not be null"));
    }
}
