package io.github.edtechdevelopment.identity.infrastructure.messaging.event;

import io.github.edtechdevelopment.identity.api.event.AccountEmailVerifiedEvent;
import io.github.edtechdevelopment.identity.api.model.AccountEmailVerificationPurpose;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.Tables.IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX;

@Repository
public class AccountEmailEventOutbox {
    private final DSLContext dsl;
    public AccountEmailEventOutbox(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void save(AccountEmailVerifiedEvent event) {
        dsl.insertInto(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX)
                .set(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.EVENT_ID, event.eventId())
                .set(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.USER_ID, event.userId())
                .set(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.EMAIL, event.email())
                .set(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.PURPOSE, event.purpose().name())
                .set(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.OCCURRED_AT, event.occurredAt().atOffset(ZoneOffset.UTC))
                .set(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.NEXT_ATTEMPT_AT, event.occurredAt().atOffset(ZoneOffset.UTC)).execute();
    }

    public List<UUID> findReadyIds(Instant now, int limit) {
        return dsl.select(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.EVENT_ID).from(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX)
                .where(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.PUBLISHED_AT.isNull())
                .and(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.NEXT_ATTEMPT_AT.le(now.atOffset(ZoneOffset.UTC)))
                .orderBy(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.NEXT_ATTEMPT_AT, IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.EVENT_ID)
                .limit(limit).fetch(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.EVENT_ID);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<AccountEmailVerifiedEvent> findReadyForUpdate(UUID id, Instant now) {
        return dsl.selectFrom(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX)
                .where(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.EVENT_ID.eq(id))
                .and(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.PUBLISHED_AT.isNull())
                .and(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.NEXT_ATTEMPT_AT.le(now.atOffset(ZoneOffset.UTC)))
                .forUpdate().skipLocked().fetchOptional().map(row -> new AccountEmailVerifiedEvent(row.getEventId(),
                        row.getUserId(), row.getEmail(), AccountEmailVerificationPurpose.valueOf(row.getPurpose()), row.getOccurredAt().toInstant()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markPublished(UUID id, Instant now) {
        dsl.update(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX).set(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.PUBLISHED_AT, now.atOffset(ZoneOffset.UTC))
                .where(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.EVENT_ID.eq(id)).and(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.PUBLISHED_AT.isNull()).execute();
    }

    public void retryLater(UUID id, Instant nextAttempt) {
        dsl.update(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX)
                .set(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.NEXT_ATTEMPT_AT, nextAttempt.atOffset(ZoneOffset.UTC))
                .where(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.EVENT_ID.eq(id)).and(IDENTITY_ACCOUNT_EMAIL_EVENT_OUTBOX.PUBLISHED_AT.isNull()).execute();
    }
}
