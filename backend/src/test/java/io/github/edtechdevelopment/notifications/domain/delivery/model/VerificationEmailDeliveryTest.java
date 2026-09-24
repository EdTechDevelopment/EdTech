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
    private static final Instant PROCESSING_AT = Instant.parse("2026-09-17T10:00:10Z");
    private static final Instant RETURNED_AT = Instant.parse("2026-09-17T10:00:20Z");
    private static final Instant SENT_AT = Instant.parse("2026-09-17T10:00:30Z");
    private static final Instant FAILED_AT = Instant.parse("2026-09-17T10:00:40Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-17T10:05:00Z");

    @Test
    void createsPendingDelivery() {
        VerificationEmailDelivery delivery = createDelivery(EXPIRES_AT);

        assertAll(
                () -> assertEquals(DELIVERY_ID, delivery.id()),
                () -> assertEquals(RECIPIENT_EMAIL, delivery.recipientEmail()),
                () -> assertEquals(CONFIRMATION_URL, delivery.confirmationUrl().orElseThrow()),
                () -> assertEquals(VerificationEmailDeliveryPurpose.REGISTRATION, delivery.purpose()),
                () -> assertEquals(DeliveryStatus.PENDING, delivery.status()),
                () -> assertEquals(CREATED_AT, delivery.createdAt()),
                () -> assertEquals(CREATED_AT, delivery.updatedAt()),
                () -> assertEquals(EXPIRES_AT, delivery.expiresAt()),
                () -> assertTrue(delivery.sentAt().isEmpty())
        );
    }

    @Test
    void reconstitutesProcessingDelivery() {
        VerificationEmailDelivery delivery = VerificationEmailDelivery.reconstitute(
                DELIVERY_ID,
                RECIPIENT_EMAIL,
                CONFIRMATION_URL,
                VerificationEmailDeliveryPurpose.REGISTRATION,
                DeliveryStatus.PROCESSING,
                EXPIRES_AT,
                CREATED_AT,
                PROCESSING_AT,
                null
        );

        assertAll(
                () -> assertEquals(DeliveryStatus.PROCESSING, delivery.status()),
                () -> assertEquals(PROCESSING_AT, delivery.updatedAt()),
                () -> assertEquals(CONFIRMATION_URL, delivery.confirmationUrl().orElseThrow()),
                () -> assertTrue(delivery.sentAt().isEmpty())
        );
    }

    @Test
    void reconstitutesSentDeliveryWithoutSensitiveUrl() {
        VerificationEmailDelivery delivery = VerificationEmailDelivery.reconstitute(
                DELIVERY_ID,
                RECIPIENT_EMAIL,
                null,
                VerificationEmailDeliveryPurpose.REGISTRATION,
                DeliveryStatus.SENT,
                EXPIRES_AT,
                CREATED_AT,
                SENT_AT,
                SENT_AT
        );

        assertAll(
                () -> assertEquals(DeliveryStatus.SENT, delivery.status()),
                () -> assertEquals(SENT_AT, delivery.updatedAt()),
                () -> assertEquals(SENT_AT, delivery.sentAt().orElseThrow()),
                () -> assertTrue(delivery.confirmationUrl().isEmpty())
        );
    }

    @Test
    void rejectsReconstitutedProcessingDeliveryWithoutConfirmationUrl() {
        assertThrows(
                InvalidVerificationEmailDeliveryException.class,
                () -> VerificationEmailDelivery.reconstitute(
                        DELIVERY_ID,
                        RECIPIENT_EMAIL,
                        null,
                        VerificationEmailDeliveryPurpose.REGISTRATION,
                        DeliveryStatus.PROCESSING,
                        EXPIRES_AT,
                        CREATED_AT,
                        PROCESSING_AT,
                        null
                )
        );
    }

    @Test
    void startsProcessingPendingDelivery() {
        VerificationEmailDelivery delivery = createDelivery(EXPIRES_AT);

        delivery.startProcessing(PROCESSING_AT);

        assertAll(
                () -> assertEquals(DeliveryStatus.PROCESSING, delivery.status()),
                () -> assertEquals(PROCESSING_AT, delivery.updatedAt()),
                () -> assertEquals(CONFIRMATION_URL, delivery.confirmationUrl().orElseThrow()),
                () -> assertTrue(delivery.sentAt().isEmpty())
        );
    }

    @Test
    void marksProcessingDeliveryAsSentAndRemovesSensitiveUrl() {
        VerificationEmailDelivery delivery = createProcessingDelivery();

        delivery.markSent(SENT_AT);

        assertAll(
                () -> assertEquals(DeliveryStatus.SENT, delivery.status()),
                () -> assertEquals(SENT_AT, delivery.updatedAt()),
                () -> assertEquals(SENT_AT, delivery.sentAt().orElseThrow()),
                () -> assertTrue(delivery.confirmationUrl().isEmpty())
        );
    }

    @Test
    void returnsProcessingDeliveryToPendingAfterTemporaryFailure() {
        VerificationEmailDelivery delivery = createProcessingDelivery();

        delivery.returnToPending(RETURNED_AT);

        assertAll(
                () -> assertEquals(DeliveryStatus.PENDING, delivery.status()),
                () -> assertEquals(RETURNED_AT, delivery.updatedAt()),
                () -> assertEquals(CONFIRMATION_URL, delivery.confirmationUrl().orElseThrow()),
                () -> assertTrue(delivery.sentAt().isEmpty())
        );
    }

    @Test
    void marksProcessingDeliveryAsFailedAndRemovesSensitiveUrl() {
        VerificationEmailDelivery delivery = createProcessingDelivery();

        delivery.markFailed(FAILED_AT);

        assertAll(
                () -> assertEquals(DeliveryStatus.FAILED, delivery.status()),
                () -> assertEquals(FAILED_AT, delivery.updatedAt()),
                () -> assertTrue(delivery.confirmationUrl().isEmpty()),
                () -> assertTrue(delivery.sentAt().isEmpty())
        );
    }

    @Test
    void marksPendingDeliveryAsExpiredAndRemovesSensitiveUrl() {
        VerificationEmailDelivery delivery = createDelivery(EXPIRES_AT);

        delivery.markExpired(EXPIRES_AT);

        assertAll(
                () -> assertEquals(DeliveryStatus.EXPIRED, delivery.status()),
                () -> assertEquals(EXPIRES_AT, delivery.updatedAt()),
                () -> assertTrue(delivery.confirmationUrl().isEmpty()),
                () -> assertTrue(delivery.sentAt().isEmpty())
        );
    }

    @Test
    void marksProcessingDeliveryAsExpired() {
        VerificationEmailDelivery delivery = createProcessingDelivery();

        delivery.markExpired(EXPIRES_AT);

        assertAll(
                () -> assertEquals(DeliveryStatus.EXPIRED, delivery.status()),
                () -> assertEquals(EXPIRES_AT, delivery.updatedAt()),
                () -> assertTrue(delivery.confirmationUrl().isEmpty())
        );
    }

    @Test
    void rejectsProcessingDeliveryAtOrAfterExpiration() {
        VerificationEmailDelivery delivery = createDelivery(EXPIRES_AT);

        assertThrows(
                InvalidVerificationEmailDeliveryException.class,
                () -> delivery.startProcessing(EXPIRES_AT)
        );
    }

    @Test
    void rejectsMarkingPendingDeliveryAsSent() {
        VerificationEmailDelivery delivery = createDelivery(EXPIRES_AT);

        assertThrows(
                InvalidVerificationEmailDeliveryException.class,
                () -> delivery.markSent(SENT_AT)
        );
    }

    @Test
    void rejectsReturningPendingDeliveryToPending() {
        VerificationEmailDelivery delivery = createDelivery(EXPIRES_AT);

        assertThrows(
                InvalidVerificationEmailDeliveryException.class,
                () -> delivery.returnToPending(RETURNED_AT)
        );
    }

    @Test
    void rejectsMarkingDeliveryAsExpiredBeforeExpirationTime() {
        VerificationEmailDelivery delivery = createDelivery(EXPIRES_AT);

        assertThrows(
                InvalidVerificationEmailDeliveryException.class,
                () -> delivery.markExpired(PROCESSING_AT)
        );
    }

    @Test
    void rejectsTransitionBeforePreviousUpdateTime() {
        VerificationEmailDelivery delivery = createProcessingDelivery();
        Instant beforeProcessing = PROCESSING_AT.minusSeconds(1);

        assertThrows(
                InvalidVerificationEmailDeliveryException.class,
                () -> delivery.returnToPending(beforeProcessing)
        );
    }

    @Test
    void rejectsTransitionFromTerminalStatus() {
        VerificationEmailDelivery delivery = createProcessingDelivery();
        delivery.markSent(SENT_AT);

        assertThrows(
                InvalidVerificationEmailDeliveryException.class,
                () -> delivery.markExpired(EXPIRES_AT)
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

    private static VerificationEmailDelivery createProcessingDelivery() {
        VerificationEmailDelivery delivery = createDelivery(EXPIRES_AT);
        delivery.startProcessing(PROCESSING_AT);
        return delivery;
    }
}
