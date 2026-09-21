package io.github.edtechdevelopment.notifications.infrastructure.persistence.mapper;

import io.github.edtechdevelopment.notifications.domain.delivery.model.DeliveryStatus;
import io.github.edtechdevelopment.notifications.domain.delivery.model.VerificationEmailDelivery;
import io.github.edtechdevelopment.notifications.domain.delivery.model.VerificationEmailDeliveryPurpose;
import io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.tables.records.NotificationEmailDeliveriesRecord;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Component
public final class VerificationEmailDeliveryPersistenceMapper {

    public VerificationEmailDelivery toDomain(NotificationEmailDeliveriesRecord record) {
        Objects.requireNonNull(record, "Verification email delivery record must not be null");

        return VerificationEmailDelivery.reconstitute(
                record.getId(),
                record.getRecipientEmail(),
                toUri(record.getConfirmationUrl()),
                VerificationEmailDeliveryPurpose.valueOf(record.getPurpose()),
                DeliveryStatus.valueOf(record.getStatus()),
                record.getExpiresAt().toInstant(),
                record.getCreatedAt().toInstant(),
                record.getUpdatedAt().toInstant(),
                toInstant(record.getSentAt())
        );
    }

    public NotificationEmailDeliveriesRecord toPersistence(VerificationEmailDelivery delivery) {
        Objects.requireNonNull(delivery, "Verification email delivery must not be null");

        return new NotificationEmailDeliveriesRecord(
                delivery.id(),
                delivery.recipientEmail(),
                delivery.confirmationUrl().map(URI::toString).orElse(null),
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

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static URI toUri(String value) {
        return value == null ? null : URI.create(value);
    }
}
