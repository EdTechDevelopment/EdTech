package io.github.edtechdevelopment.identity.infrastructure.persistence.data.repository;

import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityRefreshTokensRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityRefreshTokens.IDENTITY_REFRESH_TOKENS;

@Repository
public class RefreshTokenJooqRepository {

    private final DSLContext dslContext;

    public RefreshTokenJooqRepository(DSLContext dslContext) {
        this.dslContext = Objects.requireNonNull(dslContext, "DSL context must not be null");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<IdentityRefreshTokensRecord> findByTokenHash(String tokenHash) {
        Objects.requireNonNull(tokenHash, "Refresh token hash must not be null");

        IdentityRefreshTokensRecord record = dslContext
                .selectFrom(IDENTITY_REFRESH_TOKENS)
                .where(IDENTITY_REFRESH_TOKENS.TOKEN_HASH.eq(tokenHash))
                .fetchOne();

        return Optional.ofNullable(record);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<IdentityRefreshTokensRecord> findByTokenHashForUpdate(String tokenHash) {
        Objects.requireNonNull(tokenHash, "Refresh token hash must not be null");

        IdentityRefreshTokensRecord record = dslContext
                .selectFrom(IDENTITY_REFRESH_TOKENS)
                .where(IDENTITY_REFRESH_TOKENS.TOKEN_HASH.eq(tokenHash))
                .forUpdate()
                .fetchOne();

        return Optional.ofNullable(record);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void save(IdentityRefreshTokensRecord record) {
        Objects.requireNonNull(record, "Refresh token record must not be null");

        dslContext
                .insertInto(IDENTITY_REFRESH_TOKENS)
                .set(IDENTITY_REFRESH_TOKENS.ID, record.getId())
                .set(IDENTITY_REFRESH_TOKENS.USER_ID, record.getUserId())
                .set(IDENTITY_REFRESH_TOKENS.TOKEN_HASH, record.getTokenHash())
                .set(IDENTITY_REFRESH_TOKENS.FAMILY_ID, record.getFamilyId())
                .set(IDENTITY_REFRESH_TOKENS.EXPIRES_AT, record.getExpiresAt())
                .set(IDENTITY_REFRESH_TOKENS.REVOKED_AT, record.getRevokedAt())
                .set(IDENTITY_REFRESH_TOKENS.CREATED_AT, record.getCreatedAt())
                .execute();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revoke(UUID tokenId, OffsetDateTime revokedAt) {
        Objects.requireNonNull(tokenId, "Refresh token id must not be null");
        Objects.requireNonNull(revokedAt, "Refresh token revocation time must not be null");

        dslContext
                .update(IDENTITY_REFRESH_TOKENS)
                .set(IDENTITY_REFRESH_TOKENS.REVOKED_AT, revokedAt)
                .where(IDENTITY_REFRESH_TOKENS.ID.eq(tokenId))
                .and(IDENTITY_REFRESH_TOKENS.REVOKED_AT.isNull())
                .execute();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revokeFamily(UUID userId, UUID familyId, OffsetDateTime revokedAt) {
        Objects.requireNonNull(userId, "Refresh token user id must not be null");
        Objects.requireNonNull(familyId, "Refresh token family id must not be null");
        Objects.requireNonNull(revokedAt, "Refresh token family revocation time must not be null");

        dslContext
                .update(IDENTITY_REFRESH_TOKENS)
                .set(IDENTITY_REFRESH_TOKENS.REVOKED_AT, revokedAt)
                .where(IDENTITY_REFRESH_TOKENS.USER_ID.eq(userId))
                .and(IDENTITY_REFRESH_TOKENS.FAMILY_ID.eq(familyId))
                .and(IDENTITY_REFRESH_TOKENS.REVOKED_AT.isNull())
                .execute();
    }
}
