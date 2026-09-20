package io.github.edtechdevelopment.notifications.infrastructure.persistence.mapper;

import io.github.edtechdevelopment.notifications.domain.delivery.model.VerificationEmailDelivery;
import io.github.edtechdevelopment.notifications.domain.delivery.model.VerificationEmailDeliveryPurpose;
import io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.tables.records.NotificationEmailDeliveriesRecord;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VerificationEmailDeliveryPersistenceMapperTest {

    private static final UUID DELIVERY_ID = UUID.fromString("3a453b90-6b4c-4ee7-a384-725588e99280");
    private static final Instant CREATED_AT = Instant.parse("2026-09-17T10:00:00Z");
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
}
