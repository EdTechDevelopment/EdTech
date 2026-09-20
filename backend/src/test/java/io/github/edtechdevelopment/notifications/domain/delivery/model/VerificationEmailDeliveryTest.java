package io.github.edtechdevelopment.notifications.domain.delivery.model;

import io.github.edtechdevelopment.notifications.domain.delivery.exception.InvalidVerificationEmailDeliveryException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationEmailDeliveryTest {

    private static final UUID DELIVERY_ID = UUID.fromString("3a453b90-6b4c-4ee7-a384-725588e99280");
    private static final String RECIPIENT_EMAIL = "anna@example.com";
    private static final URI CONFIRMATION_URL = URI.create(
            "http://frontend.example:3000/verify-email?token=sensitive-token"
    );
    private static final Instant CREATED_AT = Instant.parse("2026-09-17T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-17T10:05:00Z");

    @Test
    void createsPendingDelivery() {
        VerificationEmailDelivery delivery = createDelivery(EXPIRES_AT);

        assertAll(
                () -> assertEquals(DELIVERY_ID, delivery.id()),
                () -> assertEquals(RECIPIENT_EMAIL, delivery.recipientEmail()),
                () -> assertEquals(CONFIRMATION_URL, delivery.confirmationUrl()),
                () -> assertEquals(VerificationEmailDeliveryPurpose.REGISTRATION, delivery.purpose()),
                () -> assertEquals(DeliveryStatus.PENDING, delivery.status()),
                () -> assertEquals(CREATED_AT, delivery.createdAt()),
                () -> assertEquals(CREATED_AT, delivery.updatedAt()),
                () -> assertEquals(EXPIRES_AT, delivery.expiresAt()),
                () -> assertTrue(delivery.sentAt().isEmpty())
        );
    }

    @Test
    void rejectsDeliveryThatIsAlreadyExpired() {
        assertThrows(
                InvalidVerificationEmailDeliveryException.class,
                () -> createDelivery(CREATED_AT)
        );
    }

    @Test
    void doesNotExposeSensitiveDataInStringRepresentation() {
        String representation = createDelivery(EXPIRES_AT).toString();

        assertAll(
                () -> assertFalse(representation.contains("sensitive-token")),
                () -> assertFalse(representation.contains(RECIPIENT_EMAIL)),
                () -> assertTrue(representation.contains(DELIVERY_ID.toString()))
        );
    }

    private static VerificationEmailDelivery createDelivery(Instant expiresAt) {
        return VerificationEmailDelivery.createPending(
                DELIVERY_ID,
                RECIPIENT_EMAIL,
                CONFIRMATION_URL,
                VerificationEmailDeliveryPurpose.REGISTRATION,
                CREATED_AT,
                expiresAt
        );
    }
}
