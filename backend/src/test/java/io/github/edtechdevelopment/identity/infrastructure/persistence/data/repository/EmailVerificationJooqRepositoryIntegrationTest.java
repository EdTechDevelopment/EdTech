package io.github.edtechdevelopment.identity.infrastructure.persistence.data.repository;

import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityEmailVerificationsRecord;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityEmailVerifications.IDENTITY_EMAIL_VERIFICATIONS;
import static io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.IdentityUsers.IDENTITY_USERS;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class EmailVerificationJooqRepositoryIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("334e3a59-28d3-44f9-a8fa-cec323f74fd1");
    private static final UUID VERIFICATION_ID = UUID.fromString("4d4968db-7d25-4a69-9c88-4f2e8da593e7");
    private static final Instant CREATED_AT = Instant.parse("2026-09-16T10:00:00Z");
    private static final Instant CHECKED_AT = Instant.parse("2026-09-16T10:02:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-16T10:05:00Z");

    private final EmailVerificationJooqRepository repository;
    private final DSLContext dslContext;

    @Autowired
    EmailVerificationJooqRepositoryIntegrationTest(
            EmailVerificationJooqRepository repository,
            DSLContext dslContext
    ) {
        this.repository = repository;
        this.dslContext = dslContext;
    }

    @Test
    void savesAndFindsActiveVerificationForUpdate() {
        insertUser();
        IdentityEmailVerificationsRecord record = verificationRecord(
                VERIFICATION_ID,
                "a".repeat(64),
                "REGISTRATION",
                CREATED_AT,
                EXPIRES_AT
        );
        repository.save(record);

        IdentityEmailVerificationsRecord found = repository
                .findActiveByTokenHashForUpdate(record.getTokenHash(), toOffsetDateTime(CHECKED_AT))
                .orElseThrow();

        assertAll(
                () -> assertEquals(VERIFICATION_ID, found.getId()),
                () -> assertEquals(USER_ID, found.getUserId()),
                () -> assertEquals("anna@example.com", found.getTargetEmail()),
                () -> assertNull(found.getConsumedAt()),
                () -> assertNull(found.getInvalidatedAt())
        );
    }

    @Test
    void appliesExactActivityTimeBoundaries() {
        insertUser();
        IdentityEmailVerificationsRecord record = verificationRecord(
                VERIFICATION_ID,
                "b".repeat(64),
                "REGISTRATION",
                CREATED_AT,
                EXPIRES_AT
        );
        repository.save(record);

        assertAll(
                () -> assertTrue(repository.findActiveByTokenHashForUpdate(
                        record.getTokenHash(),
                        toOffsetDateTime(CREATED_AT.minusMillis(1))
                ).isEmpty()),
                () -> assertTrue(repository.findActiveByTokenHashForUpdate(
                        record.getTokenHash(),
                        toOffsetDateTime(CREATED_AT)
                ).isPresent()),
                () -> assertTrue(repository.findActiveByTokenHashForUpdate(
                        record.getTokenHash(),
                        toOffsetDateTime(EXPIRES_AT)
                ).isEmpty())
        );
    }

    @Test
    void updatesOnlyMutableVerificationState() {
        insertUser();
        IdentityEmailVerificationsRecord original = verificationRecord(
                VERIFICATION_ID,
                "c".repeat(64),
                "REGISTRATION",
                CREATED_AT,
                EXPIRES_AT
        );
        repository.save(original);

        IdentityEmailVerificationsRecord changed = verificationRecord(
                VERIFICATION_ID,
                "d".repeat(64),
                "EMAIL_CHANGE",
                CREATED_AT.minusSeconds(60),
                EXPIRES_AT.plusSeconds(60)
        );
        changed.setTargetEmail("changed@example.com");
        changed.setConsumedAt(toOffsetDateTime(CHECKED_AT));
        repository.save(changed);

        IdentityEmailVerificationsRecord stored = dslContext
                .selectFrom(IDENTITY_EMAIL_VERIFICATIONS)
                .where(IDENTITY_EMAIL_VERIFICATIONS.ID.eq(VERIFICATION_ID))
                .fetchOne();

        assertAll(
                () -> assertEquals("c".repeat(64), stored.getTokenHash()),
                () -> assertEquals("anna@example.com", stored.getTargetEmail()),
                () -> assertEquals("REGISTRATION", stored.getPurpose()),
                () -> assertEquals(CREATED_AT, stored.getCreatedAt().toInstant()),
                () -> assertEquals(EXPIRES_AT, stored.getExpiresAt().toInstant()),
                () -> assertEquals(CHECKED_AT, stored.getConsumedAt().toInstant())
        );
    }

    @Test
    void invalidatesOnlyActiveVerificationForRequestedPurpose() {
        insertUser();
        IdentityEmailVerificationsRecord activeRegistration = verificationRecord(
                UUID.fromString("116be3ea-aeb8-4e07-a6ad-2eb5512e1dbe"),
                "1".repeat(64),
                "REGISTRATION",
                CREATED_AT,
                EXPIRES_AT
        );
        IdentityEmailVerificationsRecord expiredRegistration = verificationRecord(
                UUID.fromString("f82894a2-bdb1-4124-91fb-803220076876"),
                "2".repeat(64),
                "REGISTRATION",
                CREATED_AT.minusSeconds(600),
                CREATED_AT.minusSeconds(60)
        );
        IdentityEmailVerificationsRecord activeEmailChange = verificationRecord(
                UUID.fromString("bc88e467-7b60-47b8-a09b-7a715ab144e4"),
                "3".repeat(64),
                "EMAIL_CHANGE",
                CREATED_AT,
                EXPIRES_AT
        );
        IdentityEmailVerificationsRecord consumedRegistration = verificationRecord(
                UUID.fromString("d9d9efe5-f015-49fa-945d-67ba4edec6a6"),
                "4".repeat(64),
                "REGISTRATION",
                CREATED_AT,
                EXPIRES_AT
        );
        consumedRegistration.setConsumedAt(toOffsetDateTime(CREATED_AT.plusSeconds(60)));

        repository.save(activeRegistration);
        repository.save(expiredRegistration);
        repository.save(activeEmailChange);
        repository.save(consumedRegistration);

        int invalidatedCount = repository.invalidateActiveForUser(
                USER_ID,
                "REGISTRATION",
                toOffsetDateTime(CHECKED_AT)
        );

        assertAll(
                () -> assertEquals(1, invalidatedCount),
                () -> assertEquals(CHECKED_AT, findRecord(activeRegistration.getId()).getInvalidatedAt().toInstant()),
                () -> assertNull(findRecord(expiredRegistration.getId()).getInvalidatedAt()),
                () -> assertNull(findRecord(activeEmailChange.getId()).getInvalidatedAt()),
                () -> assertNull(findRecord(consumedRegistration.getId()).getInvalidatedAt())
        );
    }

    @Test
    void reportsDuplicateTokenHashAsTechnicalDuplicateKeyException() {
        insertUser();
        String duplicateHash = "5".repeat(64);
        repository.save(verificationRecord(
                VERIFICATION_ID,
                duplicateHash,
                "REGISTRATION",
                CREATED_AT,
                EXPIRES_AT
        ));

        assertThrows(
                DuplicateKeyException.class,
                () -> repository.save(verificationRecord(
                        UUID.fromString("1bfbf153-e293-4130-aaf3-c648331b2049"),
                        duplicateHash,
                        "REGISTRATION",
                        CREATED_AT,
                        EXPIRES_AT
                ))
        );
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void requiresTransactionForLockingAndWriteOperations() {
        IdentityEmailVerificationsRecord record = verificationRecord(
                VERIFICATION_ID,
                "6".repeat(64),
                "REGISTRATION",
                CREATED_AT,
                EXPIRES_AT
        );

        assertAll(
                () -> assertThrows(
                        IllegalTransactionStateException.class,
                        () -> repository.findActiveByTokenHashForUpdate(
                                record.getTokenHash(),
                                toOffsetDateTime(CHECKED_AT)
                        )
                ),
                () -> assertThrows(
                        IllegalTransactionStateException.class,
                        () -> repository.save(record)
                ),
                () -> assertThrows(
                        IllegalTransactionStateException.class,
                        () -> repository.invalidateActiveForUser(
                                USER_ID,
                                "REGISTRATION",
                                toOffsetDateTime(CHECKED_AT)
                        )
                )
        );
    }

    private void insertUser() {
        OffsetDateTime timestamp = toOffsetDateTime(CREATED_AT.minusSeconds(60));
        dslContext
                .insertInto(IDENTITY_USERS)
                .set(IDENTITY_USERS.ID, USER_ID)
                .set(IDENTITY_USERS.PASSWORD_HASH, "stored-password-hash")
                .set(IDENTITY_USERS.FIRST_NAME, "Anna")
                .set(IDENTITY_USERS.LAST_NAME, "Petrova")
                .set(IDENTITY_USERS.BIRTH_DATE, LocalDate.of(2000, 1, 1))
                .set(IDENTITY_USERS.STATUS, "ACTIVE")
                .set(IDENTITY_USERS.EMAIL_VERIFIED_AT, timestamp)
                .set(IDENTITY_USERS.CREATED_AT, timestamp)
                .set(IDENTITY_USERS.UPDATED_AT, timestamp)
                .execute();
    }

    private IdentityEmailVerificationsRecord findRecord(UUID verificationId) {
        return dslContext
                .selectFrom(IDENTITY_EMAIL_VERIFICATIONS)
                .where(IDENTITY_EMAIL_VERIFICATIONS.ID.eq(verificationId))
                .fetchOne();
    }

    private static IdentityEmailVerificationsRecord verificationRecord(
            UUID verificationId,
            String tokenHash,
            String purpose,
            Instant createdAt,
            Instant expiresAt
    ) {
        return new IdentityEmailVerificationsRecord(
                verificationId,
                USER_ID,
                "anna@example.com",
                tokenHash,
                purpose,
                toOffsetDateTime(expiresAt),
                null,
                null,
                toOffsetDateTime(createdAt)
        );
    }

    private static OffsetDateTime toOffsetDateTime(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
