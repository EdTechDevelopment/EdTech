package io.github.edtechdevelopment.notifications.infrastructure.persistence.mapper;

import io.github.edtechdevelopment.notifications.domain.delivery.model.VerificationEmailDelivery;
import io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.tables.records.NotificationEmailDeliveriesRecord;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Component
public final class VerificationEmailDeliveryPersistenceMapper {

    public NotificationEmailDeliveriesRecord toPersistence(VerificationEmailDelivery delivery) {
        Objects.requireNonNull(delivery, "Verification email delivery must not be null");

        return new NotificationEmailDeliveriesRecord(
                delivery.id(),
                delivery.recipientEmail(),
                delivery.confirmationUrl().toString(),
                delivery.purpose().name(),
                delivery.status().name(),
                toOffsetDateTime(delivery.expiresAt()),
                toOffsetDateTime(delivery.createdAt()),
                toOffsetDateTime(delivery.updatedAt()),
                toOffsetDateTime(delivery.sentAt().orElse(null))
        );
    }

    private static OffsetDateTime toOffsetDateTime(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
