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
import java.util.UUID;

import static io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.tables.NotificationEmailDeliveries.NOTIFICATION_EMAIL_DELIVERIES;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class VerificationEmailDeliveryJooqRepositoryIntegrationTest {

    private static final UUID DELIVERY_ID = UUID.fromString("3a453b90-6b4c-4ee7-a384-725588e99280");
    private static final Instant CREATED_AT = Instant.parse("2026-09-17T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-17T10:05:00Z");

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
                () -> assertEquals(
                        "http://frontend.example:3000/verify-email?token=sensitive-token",
                        stored.getConfirmationUrl()
                ),
                () -> assertEquals("REGISTRATION", stored.getPurpose()),
                () -> assertEquals("PENDING", stored.getStatus()),
                () -> assertEquals(CREATED_AT, stored.getCreatedAt().toInstant()),
                () -> assertEquals(CREATED_AT, stored.getUpdatedAt().toInstant()),
                () -> assertEquals(EXPIRES_AT, stored.getExpiresAt().toInstant())
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
        return new NotificationEmailDeliveriesRecord(
                DELIVERY_ID,
                "anna@example.com",
                "http://frontend.example:3000/verify-email?token=sensitive-token",
                "REGISTRATION",
                "PENDING",
                toOffsetDateTime(EXPIRES_AT),
                toOffsetDateTime(CREATED_AT),
                toOffsetDateTime(CREATED_AT),
                null
        );
    }

    private static OffsetDateTime toOffsetDateTime(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
