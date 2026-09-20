package io.github.edtechdevelopment.identity.infrastructure.messaging.event;

import io.github.edtechdevelopment.identity.api.event.UserAccountUpdatedEvent;
import io.github.edtechdevelopment.identity.api.event.UserActivatedEvent;
import io.github.edtechdevelopment.identity.api.event.UserRegisteredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SpringIntegrationEventPublisherTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-09-17T10:00:00Z");
    private static final UUID USER_ID = UUID.fromString("778be4fd-1166-40c0-89fd-08ada3642ad4");

    private ApplicationEventPublisher applicationEventPublisher;
    private SpringIntegrationEventPublisher integrationEventPublisher;

    @BeforeEach
    void setUp() {
        applicationEventPublisher = mock(ApplicationEventPublisher.class);
        integrationEventPublisher = new SpringIntegrationEventPublisher(applicationEventPublisher);
    }

    @Test
    void publishesUserRegisteredEvent() {
        UserRegisteredEvent event = new UserRegisteredEvent(
                UUID.fromString("b81bba19-61d2-4d06-9d68-c3fd84106ebf"),
                USER_ID,
                "anna@example.com",
                OCCURRED_AT
        );

        integrationEventPublisher.publish(event);

        verify(applicationEventPublisher).publishEvent(event);
    }

    @Test
    void publishesUserActivatedEvent() {
        UserActivatedEvent event = new UserActivatedEvent(
                UUID.fromString("30819385-1733-4203-86e3-b7cc86b2cda9"),
                USER_ID,
                "anna@example.com",
                OCCURRED_AT
        );

        integrationEventPublisher.publish(event);

        verify(applicationEventPublisher).publishEvent(event);
    }

    @Test
    void publishesUserAccountUpdatedEvent() {
        UserAccountUpdatedEvent event = new UserAccountUpdatedEvent(
                UUID.fromString("8f0b6d94-e6cb-4db6-ae43-c7b26124e086"),
                USER_ID,
                Set.of("firstName", "lastName"),
                OCCURRED_AT
        );

        integrationEventPublisher.publish(event);

        verify(applicationEventPublisher).publishEvent(event);
    }
}
