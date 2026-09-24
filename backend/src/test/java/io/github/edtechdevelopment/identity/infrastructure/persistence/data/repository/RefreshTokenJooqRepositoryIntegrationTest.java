package io.github.edtechdevelopment.identity.infrastructure.persistence.data.repository;

import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityRefreshTokensRecord;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityRefreshTokens.IDENTITY_REFRESH_TOKENS;
import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityUsers.IDENTITY_USERS;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class RefreshTokenJooqRepositoryIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("3af3a003-6275-44da-98da-c8cf211ece64");
    private static final UUID SECOND_USER_ID = UUID.fromString("b993b6bb-2ce9-4293-8923-5af83676486f");
    private static final UUID TOKEN_ID = UUID.fromString("943f85c7-89b7-4f8e-b625-b57c2ca0b573");
    private static final UUID FAMILY_ID = UUID.fromString("2fe7ef88-3768-4bc0-8f69-62e4405ae24d");
    private static final Instant CREATED_AT = Instant.parse("2026-09-22T12:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-22T12:00:00Z");
    private static final Instant REVOKED_AT = Instant.parse("2026-09-22T12:05:00Z");

    private final RefreshTokenJooqRepository repository;
    private final DSLContext dslContext;

    @Autowired
    RefreshTokenJooqRepositoryIntegrationTest(
            RefreshTokenJooqRepository repository,
            DSLContext dslContext
    ) {
        this.repository = repository;
        this.dslContext = dslContext;
    }

    @Test
    void savesAndFindsRevokedTokenForUpdate() {
        insertUser(USER_ID);
        IdentityRefreshTokensRecord record = tokenRecord(
                TOKEN_ID,
                USER_ID,
                "a".repeat(64),
                FAMILY_ID
        );
        repository.save(record);
        repository.revoke(TOKEN_ID, toOffsetDateTime(REVOKED_AT));

        IdentityRefreshTokensRecord found = repository
                .findByTokenHashForUpdate(record.getTokenHash())
                .orElseThrow();

        assertAll(
                () -> assertEquals(TOKEN_ID, found.getId()),
                () -> assertEquals(USER_ID, found.getUserId()),
                () -> assertEquals(FAMILY_ID, found.getFamilyId()),
                () -> assertEquals(REVOKED_AT, found.getRevokedAt().toInstant())
        );
    }

    @Test
    void findsTokenWithoutLockForPreliminaryOwnerLookup() {
        insertUser(USER_ID);
        IdentityRefreshTokensRecord record = tokenRecord(
                TOKEN_ID,
                USER_ID,
                "9".repeat(64),
                FAMILY_ID
        );
        repository.save(record);

        IdentityRefreshTokensRecord found = repository
                .findByTokenHash(record.getTokenHash())
                .orElseThrow();

        assertAll(
                () -> assertEquals(TOKEN_ID, found.getId()),
                () -> assertEquals(USER_ID, found.getUserId()),
                () -> assertEquals(FAMILY_ID, found.getFamilyId())
        );
    }

    @Test
    void doesNotOverwriteExistingRevocationTime() {
        insertUser(USER_ID);
        repository.save(tokenRecord(TOKEN_ID, USER_ID, "b".repeat(64), FAMILY_ID));
        repository.revoke(TOKEN_ID, toOffsetDateTime(REVOKED_AT));

        repository.revoke(TOKEN_ID, toOffsetDateTime(REVOKED_AT.plusSeconds(60)));

        assertEquals(REVOKED_AT, findRecord(TOKEN_ID).getRevokedAt().toInstant());
    }

    @Test
    void revokesOnlyRequestedUserAndFamily() {
        insertUser(USER_ID);
        insertUser(SECOND_USER_ID);
        UUID otherFamilyId = UUID.fromString("f044690e-f74d-4be9-a737-0637b234708d");
        UUID matchingTokenId = UUID.fromString("41c48e80-7f6e-44a7-9ca7-f28994a557e9");
        UUID otherFamilyTokenId = UUID.fromString("97e72ec3-8170-47e0-8ab5-0a106f2d13c0");
        UUID otherUserTokenId = UUID.fromString("39979bcc-12c0-4d3e-b3ab-ed3763b21faf");

        repository.save(tokenRecord(matchingTokenId, USER_ID, "c".repeat(64), FAMILY_ID));
        repository.save(tokenRecord(otherFamilyTokenId, USER_ID, "d".repeat(64), otherFamilyId));
        repository.save(tokenRecord(otherUserTokenId, SECOND_USER_ID, "e".repeat(64), FAMILY_ID));

        repository.revokeFamily(USER_ID, FAMILY_ID, toOffsetDateTime(REVOKED_AT));

        assertAll(
                () -> assertEquals(REVOKED_AT, findRecord(matchingTokenId).getRevokedAt().toInstant()),
                () -> assertNull(findRecord(otherFamilyTokenId).getRevokedAt()),
                () -> assertNull(findRecord(otherUserTokenId).getRevokedAt())
        );
    }

    @Test
    void reportsDuplicateTokenHash() {
        insertUser(USER_ID);
        String duplicateHash = "f".repeat(64);
        repository.save(tokenRecord(TOKEN_ID, USER_ID, duplicateHash, FAMILY_ID));

        assertThrows(
                DuplicateKeyException.class,
                () -> repository.save(tokenRecord(
                        UUID.fromString("e2b87ef2-2536-407d-acd9-ad7ed0742bd5"),
                        USER_ID,
                        duplicateHash,
                        FAMILY_ID
                ))
        );
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void requiresTransactionForLockingAndWriteOperations() {
        IdentityRefreshTokensRecord record = tokenRecord(
                TOKEN_ID,
                USER_ID,
                "1".repeat(64),
                FAMILY_ID
        );

        assertAll(
                () -> assertThrows(
                        IllegalTransactionStateException.class,
                        () -> repository.findByTokenHash(record.getTokenHash())
                ),
                () -> assertThrows(
                        IllegalTransactionStateException.class,
                        () -> repository.findByTokenHashForUpdate(record.getTokenHash())
                ),
                () -> assertThrows(
                        IllegalTransactionStateException.class,
                        () -> repository.save(record)
                ),
                () -> assertThrows(
                        IllegalTransactionStateException.class,
                        () -> repository.revoke(TOKEN_ID, toOffsetDateTime(REVOKED_AT))
                ),
                () -> assertThrows(
                        IllegalTransactionStateException.class,
                        () -> repository.revokeFamily(USER_ID, FAMILY_ID, toOffsetDateTime(REVOKED_AT))
                )
        );
    }

    private void insertUser(UUID userId) {
        OffsetDateTime timestamp = toOffsetDateTime(CREATED_AT.minusSeconds(60));
        dslContext
                .insertInto(IDENTITY_USERS)
                .set(IDENTITY_USERS.ID, userId)
                .set(IDENTITY_USERS.PASSWORD_HASH, "stored-password-hash")
                .set(IDENTITY_USERS.FIRST_NAME, "Anna")
                .set(IDENTITY_USERS.LAST_NAME, "Petrova")
                .set(IDENTITY_USERS.STATUS, "ACTIVE")
                .set(IDENTITY_USERS.EMAIL_VERIFIED_AT, timestamp)
                .set(IDENTITY_USERS.CREATED_AT, timestamp)
                .set(IDENTITY_USERS.UPDATED_AT, timestamp)
                .execute();
    }

    private IdentityRefreshTokensRecord findRecord(UUID tokenId) {
        return dslContext
                .selectFrom(IDENTITY_REFRESH_TOKENS)
                .where(IDENTITY_REFRESH_TOKENS.ID.eq(tokenId))
                .fetchOne();
    }

    private static IdentityRefreshTokensRecord tokenRecord(
            UUID tokenId,
            UUID userId,
            String tokenHash,
            UUID familyId
    ) {
        return new IdentityRefreshTokensRecord(
                tokenId,
                userId,
                tokenHash,
                familyId,
                toOffsetDateTime(EXPIRES_AT),
                null,
                toOffsetDateTime(CREATED_AT)
        );
    }

    private static OffsetDateTime toOffsetDateTime(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
