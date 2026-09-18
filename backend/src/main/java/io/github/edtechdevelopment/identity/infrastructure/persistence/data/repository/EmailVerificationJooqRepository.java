package io.github.edtechdevelopment.identity.infrastructure.persistence.data.repository;

import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityEmailVerificationsRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityEmailVerifications.IDENTITY_EMAIL_VERIFICATIONS;

@Repository
public class EmailVerificationJooqRepository {

    private final DSLContext dslContext;

    public EmailVerificationJooqRepository(DSLContext dslContext) {
        this.dslContext = Objects.requireNonNull(dslContext, "DSL context must not be null");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<IdentityEmailVerificationsRecord> findActiveByTokenHashForUpdate(
            String tokenHash,
            OffsetDateTime now
    ) {
        Objects.requireNonNull(tokenHash, "Verification token hash must not be null");
        Objects.requireNonNull(now, "Verification check time must not be null");

        IdentityEmailVerificationsRecord record = dslContext
                .selectFrom(IDENTITY_EMAIL_VERIFICATIONS)
                .where(IDENTITY_EMAIL_VERIFICATIONS.TOKEN_HASH.eq(tokenHash))
                .and(IDENTITY_EMAIL_VERIFICATIONS.CONSUMED_AT.isNull())
                .and(IDENTITY_EMAIL_VERIFICATIONS.INVALIDATED_AT.isNull())
                .and(IDENTITY_EMAIL_VERIFICATIONS.CREATED_AT.le(now))
                .and(IDENTITY_EMAIL_VERIFICATIONS.EXPIRES_AT.gt(now))
                .forUpdate()
                .fetchOne();

        return Optional.ofNullable(record);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void save(IdentityEmailVerificationsRecord record) {
        Objects.requireNonNull(record, "Email verification record must not be null");

        dslContext
                .insertInto(IDENTITY_EMAIL_VERIFICATIONS)
                .set(IDENTITY_EMAIL_VERIFICATIONS.ID, record.getId())
                .set(IDENTITY_EMAIL_VERIFICATIONS.USER_ID, record.getUserId())
                .set(IDENTITY_EMAIL_VERIFICATIONS.TARGET_EMAIL, record.getTargetEmail())
                .set(IDENTITY_EMAIL_VERIFICATIONS.TOKEN_HASH, record.getTokenHash())
                .set(IDENTITY_EMAIL_VERIFICATIONS.PURPOSE, record.getPurpose())
                .set(IDENTITY_EMAIL_VERIFICATIONS.EXPIRES_AT, record.getExpiresAt())
                .set(IDENTITY_EMAIL_VERIFICATIONS.CONSUMED_AT, record.getConsumedAt())
                .set(IDENTITY_EMAIL_VERIFICATIONS.INVALIDATED_AT, record.getInvalidatedAt())
                .set(IDENTITY_EMAIL_VERIFICATIONS.CREATED_AT, record.getCreatedAt())
                .onConflict(IDENTITY_EMAIL_VERIFICATIONS.ID)
                .doUpdate()
                .set(IDENTITY_EMAIL_VERIFICATIONS.CONSUMED_AT, record.getConsumedAt())
                .set(IDENTITY_EMAIL_VERIFICATIONS.INVALIDATED_AT, record.getInvalidatedAt())
                .execute();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int invalidateActiveForUser(
            UUID userId,
            String purpose,
            OffsetDateTime invalidatedAt
    ) {
        Objects.requireNonNull(userId, "User id must not be null");
        Objects.requireNonNull(purpose, "Verification purpose must not be null");
        Objects.requireNonNull(invalidatedAt, "Invalidation time must not be null");

        return dslContext
                .update(IDENTITY_EMAIL_VERIFICATIONS)
                .set(IDENTITY_EMAIL_VERIFICATIONS.INVALIDATED_AT, invalidatedAt)
                .where(IDENTITY_EMAIL_VERIFICATIONS.USER_ID.eq(userId))
                .and(IDENTITY_EMAIL_VERIFICATIONS.PURPOSE.eq(purpose))
                .and(IDENTITY_EMAIL_VERIFICATIONS.CONSUMED_AT.isNull())
                .and(IDENTITY_EMAIL_VERIFICATIONS.INVALIDATED_AT.isNull())
                .and(IDENTITY_EMAIL_VERIFICATIONS.CREATED_AT.le(invalidatedAt))
                .and(IDENTITY_EMAIL_VERIFICATIONS.EXPIRES_AT.gt(invalidatedAt))
                .execute();
    }
}
