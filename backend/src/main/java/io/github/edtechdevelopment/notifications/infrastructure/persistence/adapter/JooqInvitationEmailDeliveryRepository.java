package io.github.edtechdevelopment.notifications.infrastructure.persistence.adapter;

import io.github.edtechdevelopment.notifications.application.model.ClaimedInvitationEmail;
import io.github.edtechdevelopment.notifications.application.port.out.persistence.InvitationEmailDeliveryRepository;
import io.github.edtechdevelopment.notifications.domain.delivery.model.DeliveryStatus;
import io.github.edtechdevelopment.notifications.domain.delivery.model.InvitationEmailDelivery;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.github.edtechdevelopment.notifications.infrastructure.persistence.data.generated.Tables.NOTIFICATION_INVITATION_EMAIL_DELIVERIES;
import static org.jooq.impl.DSL.val;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JooqInvitationEmailDeliveryRepository implements InvitationEmailDeliveryRepository {
    private final DSLContext dsl;
    public JooqInvitationEmailDeliveryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void enqueue(InvitationEmailDelivery delivery) {
        int inserted = dsl.insertInto(NOTIFICATION_INVITATION_EMAIL_DELIVERIES)
                .set(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.ID, delivery.id())
                .set(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.INVITATION_ID, delivery.invitationId())
                .set(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.DEDUPLICATION_KEY, delivery.deduplicationKey())
                .set(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.RECIPIENT_EMAIL, delivery.recipientEmail())
                .set(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.TEACHER_DISPLAY_NAME, delivery.teacherDisplayName())
                .set(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.INVITATION_URL, delivery.invitationUrl().toString())
                .set(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.STATUS, "PENDING")
                .set(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.EXPIRES_AT, delivery.expiresAt().atOffset(ZoneOffset.UTC))
                .set(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.CREATED_AT, delivery.createdAt().atOffset(ZoneOffset.UTC))
                .set(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.AVAILABLE_AT, delivery.createdAt().atOffset(ZoneOffset.UTC))
                .onConflict(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.DEDUPLICATION_KEY).doNothing().execute();
        if (inserted == 0) {
            var existing = dsl.selectFrom(NOTIFICATION_INVITATION_EMAIL_DELIVERIES)
                    .where(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.DEDUPLICATION_KEY.eq(delivery.deduplicationKey())).fetchOne();
            if (existing == null || !existing.getInvitationId().equals(delivery.invitationId())
                    || !existing.getRecipientEmail().equals(delivery.recipientEmail())
                    || !existing.getTeacherDisplayName().equals(delivery.teacherDisplayName())
                    || !existing.getExpiresAt().toInstant().equals(delivery.expiresAt())) {
                throw new IllegalStateException("Invitation notification key conflicts with stored data");
            }
        }
    }

    @Override
    public List<ClaimedInvitationEmail> claimPending(Instant now, Instant staleBefore, int limit) {
        var time = now.atOffset(ZoneOffset.UTC);
        dsl.update(NOTIFICATION_INVITATION_EMAIL_DELIVERIES)
                .set(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.STATUS, "EXPIRED")
                .set(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.INVITATION_URL, (String) null)
                .set(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.CLAIM_ID, (UUID) null)
                .set(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.CLAIMED_AT, (OffsetDateTime) null)
                .where(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.STATUS.in("PENDING", "PROCESSING"))
                .and(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.EXPIRES_AT.le(time)).execute();
        dsl.update(NOTIFICATION_INVITATION_EMAIL_DELIVERIES)
                .set(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.STATUS, "PENDING")
                .set(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.CLAIM_ID, (UUID) null)
                .set(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.CLAIMED_AT, (OffsetDateTime) null)
                .set(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.AVAILABLE_AT, time)
                .where(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.STATUS.eq("PROCESSING"))
                .and(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.CLAIMED_AT.le(staleBefore.atOffset(ZoneOffset.UTC))).execute();
        var rows = dsl.selectFrom(NOTIFICATION_INVITATION_EMAIL_DELIVERIES)
                .where(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.STATUS.eq("PENDING"))
                .and(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.AVAILABLE_AT.le(time))
                .orderBy(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.AVAILABLE_AT, NOTIFICATION_INVITATION_EMAIL_DELIVERIES.ID)
                .limit(limit).forUpdate().skipLocked().fetch();
        List<ClaimedInvitationEmail> claimed = new ArrayList<>();
        for (var row : rows) {
            UUID claimId = UUID.randomUUID();
            dsl.update(NOTIFICATION_INVITATION_EMAIL_DELIVERIES)
                    .set(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.STATUS, "PROCESSING")
                    .set(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.CLAIM_ID, claimId)
                    .set(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.CLAIMED_AT, time)
                    .where(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.ID.eq(row.getId())).execute();
            claimed.add(new ClaimedInvitationEmail(new InvitationEmailDelivery(row.getId(), row.getInvitationId(),
                    row.getRecipientEmail(), row.getTeacherDisplayName(), URI.create(row.getInvitationUrl()),
                    row.getExpiresAt().toInstant(), row.getCreatedAt().toInstant(), row.getDeduplicationKey()), claimId));
        }
        return List.copyOf(claimed);
    }

    @Override
    public boolean complete(UUID id, UUID claimId, DeliveryStatus requested, Instant now) {
        if (requested == DeliveryStatus.PROCESSING) {
            throw new IllegalArgumentException("Invalid delivery completion");
        }
        DeliveryStatus status = requested;
        var row = dsl.selectFrom(NOTIFICATION_INVITATION_EMAIL_DELIVERIES)
                .where(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.ID.eq(id))
                .and(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.CLAIM_ID.eq(claimId))
                .and(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.STATUS.eq("PROCESSING")).forUpdate().fetchOne();
        if (row == null) {
            return false;
        }
        if (status == DeliveryStatus.PENDING && !now.isBefore(row.getExpiresAt().toInstant())) {
            status = DeliveryStatus.EXPIRED;
        }
        Field<String> url = status == DeliveryStatus.PENDING ? NOTIFICATION_INVITATION_EMAIL_DELIVERIES.INVITATION_URL : val((String) null);
        return dsl.update(NOTIFICATION_INVITATION_EMAIL_DELIVERIES)
                .set(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.STATUS, status.name())
                .set(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.CLAIM_ID, (UUID) null)
                .set(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.CLAIMED_AT, (OffsetDateTime) null)
                .set(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.INVITATION_URL, url)
                .set(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.SENT_AT, status == DeliveryStatus.SENT ? now.atOffset(ZoneOffset.UTC) : null)
                .set(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.AVAILABLE_AT, now.plusSeconds(10).atOffset(ZoneOffset.UTC))
                .where(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.ID.eq(id))
                .and(NOTIFICATION_INVITATION_EMAIL_DELIVERIES.CLAIM_ID.eq(claimId)).execute() == 1;
    }
}
