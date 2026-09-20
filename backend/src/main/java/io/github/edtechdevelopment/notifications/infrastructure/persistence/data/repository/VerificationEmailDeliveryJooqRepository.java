package io.github.edtechdevelopment.notifications.infrastructure.persistence.data.repository;

import io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.tables.records.NotificationEmailDeliveriesRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.tables.NotificationEmailDeliveries.NOTIFICATION_EMAIL_DELIVERIES;

@Repository
public class VerificationEmailDeliveryJooqRepository {

    private final DSLContext dslContext;

    public VerificationEmailDeliveryJooqRepository(DSLContext dslContext) {
        this.dslContext = Objects.requireNonNull(dslContext, "DSL context must not be null");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void save(NotificationEmailDeliveriesRecord record) {
        Objects.requireNonNull(record, "Verification email delivery record must not be null");

        dslContext
                .insertInto(NOTIFICATION_EMAIL_DELIVERIES)
                .set(NOTIFICATION_EMAIL_DELIVERIES.ID, record.getId())
                .set(NOTIFICATION_EMAIL_DELIVERIES.RECIPIENT_EMAIL, record.getRecipientEmail())
                .set(NOTIFICATION_EMAIL_DELIVERIES.CONFIRMATION_URL, record.getConfirmationUrl())
                .set(NOTIFICATION_EMAIL_DELIVERIES.PURPOSE, record.getPurpose())
                .set(NOTIFICATION_EMAIL_DELIVERIES.STATUS, record.getStatus())
                .set(NOTIFICATION_EMAIL_DELIVERIES.EXPIRES_AT, record.getExpiresAt())
                .set(NOTIFICATION_EMAIL_DELIVERIES.CREATED_AT, record.getCreatedAt())
                .set(NOTIFICATION_EMAIL_DELIVERIES.UPDATED_AT, record.getUpdatedAt())
                .set(NOTIFICATION_EMAIL_DELIVERIES.SENT_AT, record.getSentAt())
                .execute();
    }
}
