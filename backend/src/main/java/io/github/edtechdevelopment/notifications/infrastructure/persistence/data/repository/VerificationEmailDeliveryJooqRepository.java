package io.github.edtechdevelopment.notifications.infrastructure.persistence.data.repository;

import io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.tables.records.NotificationEmailDeliveriesRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
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

    @Transactional(propagation = Propagation.MANDATORY)
    public List<NotificationEmailDeliveriesRecord> findPending(
            OffsetDateTime now,
            int limit
    ) {
        Objects.requireNonNull(now, "Pending delivery selection time must not be null");
        requirePositiveLimit(limit);

        return dslContext
                .selectFrom(NOTIFICATION_EMAIL_DELIVERIES)
                .where(NOTIFICATION_EMAIL_DELIVERIES.STATUS.eq("PENDING"))
                .and(NOTIFICATION_EMAIL_DELIVERIES.EXPIRES_AT.gt(now))
                .orderBy(
                        NOTIFICATION_EMAIL_DELIVERIES.CREATED_AT.asc(),
                        NOTIFICATION_EMAIL_DELIVERIES.ID.asc()
                )
                .limit(limit)
                .fetch();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<NotificationEmailDeliveriesRecord> findStaleProcessing(
            OffsetDateTime staleBefore,
            OffsetDateTime now,
            int limit
    ) {
        Objects.requireNonNull(staleBefore, "Stale processing threshold must not be null");
        Objects.requireNonNull(now, "Stale delivery selection time must not be null");
        requirePositiveLimit(limit);

        return dslContext
                .selectFrom(NOTIFICATION_EMAIL_DELIVERIES)
                .where(NOTIFICATION_EMAIL_DELIVERIES.STATUS.eq("PROCESSING"))
                .and(NOTIFICATION_EMAIL_DELIVERIES.UPDATED_AT.le(staleBefore))
                .and(NOTIFICATION_EMAIL_DELIVERIES.EXPIRES_AT.gt(now))
                .orderBy(
                        NOTIFICATION_EMAIL_DELIVERIES.UPDATED_AT.asc(),
                        NOTIFICATION_EMAIL_DELIVERIES.ID.asc()
                )
                .limit(limit)
                .fetch();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<NotificationEmailDeliveriesRecord> findExpired(
            OffsetDateTime now,
            int limit
    ) {
        Objects.requireNonNull(now, "Expired delivery selection time must not be null");
        requirePositiveLimit(limit);

        return dslContext
                .selectFrom(NOTIFICATION_EMAIL_DELIVERIES)
                .where(NOTIFICATION_EMAIL_DELIVERIES.STATUS.in("PENDING", "PROCESSING"))
                .and(NOTIFICATION_EMAIL_DELIVERIES.EXPIRES_AT.le(now))
                .orderBy(
                        NOTIFICATION_EMAIL_DELIVERIES.EXPIRES_AT.asc(),
                        NOTIFICATION_EMAIL_DELIVERIES.ID.asc()
                )
                .limit(limit)
                .fetch();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void update(NotificationEmailDeliveriesRecord record) {
        Objects.requireNonNull(record, "Verification email delivery record must not be null");

        int updatedRows = dslContext
                .update(NOTIFICATION_EMAIL_DELIVERIES)
                .set(NOTIFICATION_EMAIL_DELIVERIES.CONFIRMATION_URL, record.getConfirmationUrl())
                .set(NOTIFICATION_EMAIL_DELIVERIES.STATUS, record.getStatus())
                .set(NOTIFICATION_EMAIL_DELIVERIES.UPDATED_AT, record.getUpdatedAt())
                .set(NOTIFICATION_EMAIL_DELIVERIES.SENT_AT, record.getSentAt())
                .where(NOTIFICATION_EMAIL_DELIVERIES.ID.eq(record.getId()))
                .execute();

        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "Expected to update one verification email delivery, but updated " + updatedRows
            );
        }
    }

    private static void requirePositiveLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Delivery selection limit must be positive");
        }
    }
}
