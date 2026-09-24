package io.github.edtechdevelopment.notifications.infrastructure.persistence.mapper;

import io.github.edtechdevelopment.notifications.domain.delivery.model.VerificationEmailDelivery;
import io.github.edtechdevelopment.notifications.domain.delivery.model.VerificationEmailDeliveryPurpose;
import io.github.edtechdevelopment.notifications.domain.delivery.model.DeliveryStatus;
import io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.tables.records.NotificationEmailDeliveriesRecord;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationEmailDeliveryPersistenceMapperTest {

    private static final UUID DELIVERY_ID = UUID.fromString("3a453b90-6b4c-4ee7-a384-725588e99280");
    private static final Instant CREATED_AT = Instant.parse("2026-09-17T10:00:00Z");
    private static final Instant PROCESSING_AT = Instant.parse("2026-09-17T10:00:10Z");
    private static final Instant SENT_AT = Instant.parse("2026-09-17T10:00:20Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-17T10:05:00Z");

    @Test
    void mapsPendingDeliveryToJooqRecord() {
        URI confirmationUrl = URI.create(
                "http://frontend.example:3000/verify-email?token=sensitive-token"
        );
        VerificationEmailDelivery delivery = VerificationEmailDelivery.createPending(
                DELIVERY_ID,
                "anna@example.com",
                confirmationUrl,
                VerificationEmailDeliveryPurpose.REGISTRATION,
                CREATED_AT,
                EXPIRES_AT
        );

        NotificationEmailDeliveriesRecord record =
                new VerificationEmailDeliveryPersistenceMapper().toPersistence(delivery);

        assertAll(
                () -> assertEquals(DELIVERY_ID, record.getId()),
                () -> assertEquals("anna@example.com", record.getRecipientEmail()),
                () -> assertEquals(confirmationUrl.toString(), record.getConfirmationUrl()),
                () -> assertEquals("REGISTRATION", record.getPurpose()),
                () -> assertEquals("PENDING", record.getStatus()),
                () -> assertEquals(EXPIRES_AT, record.getExpiresAt().toInstant()),
                () -> assertEquals(ZoneOffset.UTC, record.getExpiresAt().getOffset()),
                () -> assertEquals(CREATED_AT, record.getCreatedAt().toInstant()),
                () -> assertEquals(CREATED_AT, record.getUpdatedAt().toInstant()),
                () -> assertNull(record.getSentAt())
        );
    }

    @Test
    void mapsSentDeliveryWithoutSensitiveConfirmationUrl() {
        VerificationEmailDelivery delivery = VerificationEmailDelivery.createPending(
                DELIVERY_ID,
                "anna@example.com",
                URI.create("http://frontend.example:3000/verify-email?token=sensitive-token"),
                VerificationEmailDeliveryPurpose.REGISTRATION,
                CREATED_AT,
                EXPIRES_AT
        );
        delivery.startProcessing(PROCESSING_AT);
        delivery.markSent(SENT_AT);

        NotificationEmailDeliveriesRecord record =
                new VerificationEmailDeliveryPersistenceMapper().toPersistence(delivery);

        assertAll(
                () -> assertEquals("SENT", record.getStatus()),
                () -> assertEquals(SENT_AT, record.getUpdatedAt().toInstant()),
                () -> assertEquals(SENT_AT, record.getSentAt().toInstant()),
                () -> assertNull(record.getConfirmationUrl())
        );
    }

    @Test
    void mapsJooqRecordToProcessingDelivery() {
        URI confirmationUrl = URI.create(
                "http://frontend.example:3000/verify-email?token=sensitive-token"
        );
        NotificationEmailDeliveriesRecord record = new NotificationEmailDeliveriesRecord(
                DELIVERY_ID,
                "anna@example.com",
                confirmationUrl.toString(),
                "REGISTRATION",
                "PROCESSING",
                EXPIRES_AT.atOffset(ZoneOffset.UTC),
                CREATED_AT.atOffset(ZoneOffset.UTC),
                PROCESSING_AT.atOffset(ZoneOffset.UTC),
                null
        );

        VerificationEmailDelivery delivery =
                new VerificationEmailDeliveryPersistenceMapper().toDomain(record);

        assertAll(
                () -> assertEquals(DELIVERY_ID, delivery.id()),
                () -> assertEquals("anna@example.com", delivery.recipientEmail()),
                () -> assertEquals(confirmationUrl, delivery.confirmationUrl().orElseThrow()),
                () -> assertEquals(VerificationEmailDeliveryPurpose.REGISTRATION, delivery.purpose()),
                () -> assertEquals(DeliveryStatus.PROCESSING, delivery.status()),
                () -> assertEquals(EXPIRES_AT, delivery.expiresAt()),
                () -> assertEquals(CREATED_AT, delivery.createdAt()),
                () -> assertEquals(PROCESSING_AT, delivery.updatedAt()),
                () -> assertTrue(delivery.sentAt().isEmpty())
        );
    }

    @Test
    void mapsSentJooqRecordWithoutSensitiveConfirmationUrl() {
        NotificationEmailDeliveriesRecord record = new NotificationEmailDeliveriesRecord(
                DELIVERY_ID,
                "anna@example.com",
                null,
                "REGISTRATION",
                "SENT",
                EXPIRES_AT.atOffset(ZoneOffset.UTC),
                CREATED_AT.atOffset(ZoneOffset.UTC),
                SENT_AT.atOffset(ZoneOffset.UTC),
                SENT_AT.atOffset(ZoneOffset.UTC)
        );

        VerificationEmailDelivery delivery =
                new VerificationEmailDeliveryPersistenceMapper().toDomain(record);

        assertAll(
                () -> assertEquals(DeliveryStatus.SENT, delivery.status()),
                () -> assertEquals(SENT_AT, delivery.updatedAt()),
                () -> assertEquals(SENT_AT, delivery.sentAt().orElseThrow()),
                () -> assertTrue(delivery.confirmationUrl().isEmpty())
        );
    }
}
