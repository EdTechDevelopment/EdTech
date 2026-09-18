package io.github.edtechdevelopment.notifications.application.service;

import io.github.edtechdevelopment.notifications.api.command.SendVerificationEmailCommand;
import io.github.edtechdevelopment.notifications.api.model.VerificationEmailPurpose;
import io.github.edtechdevelopment.notifications.application.port.out.TimeProvider;
import io.github.edtechdevelopment.notifications.application.port.out.persistence.VerificationEmailDeliveryRepository;
import io.github.edtechdevelopment.notifications.domain.delivery.model.DeliveryStatus;
import io.github.edtechdevelopment.notifications.domain.delivery.model.VerificationEmailDelivery;
import io.github.edtechdevelopment.notifications.domain.delivery.model.VerificationEmailDeliveryPurpose;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnqueueVerificationEmailServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-17T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-17T10:05:00Z");
    private static final URI CONFIRMATION_URL = URI.create(
            "http://frontend.example:3000/verify-email?token=sensitive-token"
    );

    @Test
    void createsAndSavesPendingDelivery() {
        VerificationEmailDeliveryRepository repository = mock(VerificationEmailDeliveryRepository.class);
        TimeProvider timeProvider = mock(TimeProvider.class);
        when(timeProvider.now()).thenReturn(CREATED_AT);
        EnqueueVerificationEmailService service = new EnqueueVerificationEmailService(repository, timeProvider);

        service.enqueue(new SendVerificationEmailCommand(
                "anna@example.com",
                CONFIRMATION_URL,
                VerificationEmailPurpose.REGISTRATION,
                EXPIRES_AT
        ));

        ArgumentCaptor<VerificationEmailDelivery> deliveryCaptor =
                ArgumentCaptor.forClass(VerificationEmailDelivery.class);
        verify(repository).save(deliveryCaptor.capture());
        VerificationEmailDelivery delivery = deliveryCaptor.getValue();

        assertAll(
                () -> assertNotNull(delivery.id()),
                () -> assertEquals("anna@example.com", delivery.recipientEmail()),
                () -> assertEquals(CONFIRMATION_URL, delivery.confirmationUrl()),
                () -> assertEquals(VerificationEmailDeliveryPurpose.REGISTRATION, delivery.purpose()),
                () -> assertEquals(DeliveryStatus.PENDING, delivery.status()),
                () -> assertEquals(CREATED_AT, delivery.createdAt()),
                () -> assertEquals(CREATED_AT, delivery.updatedAt()),
                () -> assertEquals(EXPIRES_AT, delivery.expiresAt()),
                () -> assertTrue(delivery.sentAt().isEmpty())
        );
    }
}
