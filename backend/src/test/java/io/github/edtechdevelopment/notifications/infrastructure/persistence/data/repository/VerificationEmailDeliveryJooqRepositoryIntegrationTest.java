package io.github.edtechdevelopment.notifications.infrastructure.persistence.data.repository;

import io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.tables.records.NotificationEmailDeliveriesRecord;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.tables.NotificationEmailDeliveries.NOTIFICATION_EMAIL_DELIVERIES;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class VerificationEmailDeliveryJooqRepositoryIntegrationTest {

    private static final UUID DELIVERY_ID = UUID.fromString("3a453b90-6b4c-4ee7-a384-725588e99280");
    private static final UUID SECOND_DELIVERY_ID = UUID.fromString("5b977320-43f7-4946-ae40-c023453ae6cb");
    private static final UUID THIRD_DELIVERY_ID = UUID.fromString("84b0f4ee-d9a5-46d2-8377-6e3bc29f3540");
    private static final Instant CREATED_AT = Instant.parse("2026-09-17T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-17T10:05:00Z");
    private static final String CONFIRMATION_URL =
            "http://frontend.example:3000/verify-email?token=sensitive-token";

    private final VerificationEmailDeliveryJooqRepository repository;
    private final DSLContext dslContext;

    @Autowired
    VerificationEmailDeliveryJooqRepositoryIntegrationTest(
            VerificationEmailDeliveryJooqRepository repository,
            DSLContext dslContext
    ) {
        this.repository = repository;
        this.dslContext = dslContext;
    }

    @Test
    void savesPendingDelivery() {
        repository.save(deliveryRecord());

        NotificationEmailDeliveriesRecord stored = dslContext
                .selectFrom(NOTIFICATION_EMAIL_DELIVERIES)
                .where(NOTIFICATION_EMAIL_DELIVERIES.ID.eq(DELIVERY_ID))
                .fetchOne();

        assertNotNull(stored);
        assertAll(
                () -> assertEquals("anna@example.com", stored.getRecipientEmail()),
                () -> assertEquals(CONFIRMATION_URL, stored.getConfirmationUrl()),
                () -> assertEquals("REGISTRATION", stored.getPurpose()),
                () -> assertEquals("PENDING", stored.getStatus()),
                () -> assertEquals(CREATED_AT, stored.getCreatedAt().toInstant()),
                () -> assertEquals(CREATED_AT, stored.getUpdatedAt().toInstant()),
                () -> assertEquals(EXPIRES_AT, stored.getExpiresAt().toInstant())
        );
    }

    @Test
    void findsOldestNonExpiredPendingDeliveriesWithinLimit() {
        Instant now = CREATED_AT.plusSeconds(90);
        repository.save(deliveryRecord(
                DELIVERY_ID,
                "PENDING",
                CREATED_AT.minusSeconds(60),
                CREATED_AT.minusSeconds(60),
                EXPIRES_AT,
                CONFIRMATION_URL,
                null
        ));
        repository.save(deliveryRecord(
                SECOND_DELIVERY_ID,
                "PENDING",
                CREATED_AT,
                CREATED_AT,
                EXPIRES_AT,
                CONFIRMATION_URL,
                null
        ));
        repository.save(deliveryRecord(
                THIRD_DELIVERY_ID,
                "PENDING",
                CREATED_AT,
                CREATED_AT,
                now,
                CONFIRMATION_URL,
                null
        ));

        List<NotificationEmailDeliveriesRecord> records = repository.findPending(
                toOffsetDateTime(now),
                1
        );

        assertAll(
                () -> assertEquals(1, records.size()),
                () -> assertEquals(DELIVERY_ID, records.getFirst().getId())
        );
    }

    @Test
    void findsOnlyStaleNonExpiredProcessingDeliveries() {
        Instant now = CREATED_AT.plusSeconds(240);
        Instant staleBefore = now.minusSeconds(60);
        repository.save(deliveryRecord(
                DELIVERY_ID,
                "PROCESSING",
                CREATED_AT,
                staleBefore.minusSeconds(1),
                EXPIRES_AT,
                CONFIRMATION_URL,
                null
        ));
        repository.save(deliveryRecord(
                SECOND_DELIVERY_ID,
                "PROCESSING",
                CREATED_AT,
                staleBefore.plusSeconds(1),
                EXPIRES_AT,
                CONFIRMATION_URL,
                null
        ));
        repository.save(deliveryRecord(
                THIRD_DELIVERY_ID,
                "PROCESSING",
                CREATED_AT,
                CREATED_AT.plusSeconds(120),
                now,
                CONFIRMATION_URL,
                null
        ));

        List<NotificationEmailDeliveriesRecord> records = repository.findStaleProcessing(
                toOffsetDateTime(staleBefore),
                toOffsetDateTime(now),
                10
        );

        assertAll(
                () -> assertEquals(1, records.size()),
                () -> assertEquals(DELIVERY_ID, records.getFirst().getId())
        );
    }

    @Test
    void findsExpiredPendingAndProcessingDeliveries() {
        Instant now = EXPIRES_AT;
        repository.save(deliveryRecord(
                DELIVERY_ID,
                "PENDING",
                CREATED_AT,
                CREATED_AT,
                now,
                CONFIRMATION_URL,
                null
        ));
        repository.save(deliveryRecord(
                SECOND_DELIVERY_ID,
                "PROCESSING",
                CREATED_AT,
                CREATED_AT.plusSeconds(60),
                now.minusSeconds(1),
                CONFIRMATION_URL,
                null
        ));
        repository.save(deliveryRecord(
                THIRD_DELIVERY_ID,
                "SENT",
                CREATED_AT,
                CREATED_AT.plusSeconds(120),
                now,
                null,
                CREATED_AT.plusSeconds(120)
        ));

        List<NotificationEmailDeliveriesRecord> records = repository.findExpired(
                toOffsetDateTime(now),
                10
        );

        assertEquals(
                List.of(SECOND_DELIVERY_ID, DELIVERY_ID),
                records.stream().map(NotificationEmailDeliveriesRecord::getId).toList()
        );
    }

    @Test
    void updatesMutableDeliveryState() {
        repository.save(deliveryRecord());
        Instant sentAt = CREATED_AT.plusSeconds(30);
        NotificationEmailDeliveriesRecord sentRecord = deliveryRecord(
                DELIVERY_ID,
                "SENT",
                CREATED_AT,
                sentAt,
                EXPIRES_AT,
                null,
                sentAt
        );

        repository.update(sentRecord);

        NotificationEmailDeliveriesRecord stored = dslContext
                .selectFrom(NOTIFICATION_EMAIL_DELIVERIES)
                .where(NOTIFICATION_EMAIL_DELIVERIES.ID.eq(DELIVERY_ID))
                .fetchOne();

        assertNotNull(stored);
        assertAll(
                () -> assertEquals("SENT", stored.getStatus()),
                () -> assertEquals(sentAt, stored.getUpdatedAt().toInstant()),
                () -> assertEquals(sentAt, stored.getSentAt().toInstant()),
                () -> assertNull(stored.getConfirmationUrl())
        );
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void requiresExistingTransaction() {
        assertThrows(
                IllegalTransactionStateException.class,
                () -> repository.save(deliveryRecord())
        );
    }

    private static NotificationEmailDeliveriesRecord deliveryRecord() {
        return deliveryRecord(
                DELIVERY_ID,
                "PENDING",
                CREATED_AT,
                CREATED_AT,
                EXPIRES_AT,
                CONFIRMATION_URL,
                null
        );
    }

    private static NotificationEmailDeliveriesRecord deliveryRecord(
            UUID id,
            String status,
            Instant createdAt,
            Instant updatedAt,
            Instant expiresAt,
            String confirmationUrl,
            Instant sentAt
    ) {
        return new NotificationEmailDeliveriesRecord(
                id,
                "anna@example.com",
                confirmationUrl,
                "REGISTRATION",
                status,
                toOffsetDateTime(expiresAt),
                toOffsetDateTime(createdAt),
                toOffsetDateTime(updatedAt),
                sentAt == null ? null : toOffsetDateTime(sentAt)
        );
    }

    private static OffsetDateTime toOffsetDateTime(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
