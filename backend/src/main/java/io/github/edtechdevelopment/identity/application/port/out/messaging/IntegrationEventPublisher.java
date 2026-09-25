package io.github.edtechdevelopment.identity.application.port.out.messaging;

import io.github.edtechdevelopment.identity.api.event.AccountEmailVerifiedEvent;
import io.github.edtechdevelopment.identity.api.event.UserAccountUpdatedEvent;
import io.github.edtechdevelopment.identity.api.event.UserActivatedEvent;
import io.github.edtechdevelopment.identity.api.event.UserRegisteredEvent;

public interface IntegrationEventPublisher {

    void publish(AccountEmailVerifiedEvent event);

    void publish(UserRegisteredEvent event);

    void publish(UserActivatedEvent event);

    void publish(UserAccountUpdatedEvent event);
}
