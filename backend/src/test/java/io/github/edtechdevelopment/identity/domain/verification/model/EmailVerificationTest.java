package io.github.edtechdevelopment.identity.domain.verification.model;

import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.verification.exception.InvalidEmailVerificationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailVerificationTest {

    private static final UUID VERIFICATION_ID = UUID.fromString("81580d5d-b71f-41dd-930a-6788f41c3d08");
    private static final UUID USER_ID = UUID.fromString("25ca25ce-c1c7-4d4a-8cf6-ffb72618ad19");
    private static final Email TARGET_EMAIL = new Email("anna@example.com");
    private static final VerificationTokenHash TOKEN_HASH = new VerificationTokenHash("verification-token-hash");
    private static final Instant CREATED_AT = Instant.parse("2026-09-13T12:00:00Z");
    private static final Instant EXPIRES_AT = CREATED_AT.plusSeconds(900);

    @Test
    void createsUnusedVerification() {
        EmailVerification verification = createVerification();

        assertEquals(VERIFICATION_ID, verification.id());
        assertEquals(USER_ID, verification.userId());
        assertEquals(TARGET_EMAIL, verification.targetEmail());
        assertEquals(TOKEN_HASH, verification.tokenHash());
        assertEquals(VerificationPurpose.REGISTRATION, verification.purpose());
        assertEquals(CREATED_AT, verification.createdAt());
        assertEquals(EXPIRES_AT, verification.expiresAt());
        assertTrue(verification.consumedAt().isEmpty());
        assertTrue(verification.invalidatedAt().isEmpty());
    }

    @Test
    void isActiveOnlyDuringValidityPeriod() {
        EmailVerification verification = createVerification();

        assertFalse(verification.isActiveAt(CREATED_AT.minusNanos(1)));
        assertTrue(verification.isActiveAt(CREATED_AT));
        assertTrue(verification.isActiveAt(EXPIRES_AT.minusNanos(1)));
        assertFalse(verification.isActiveAt(EXPIRES_AT));
        assertFalse(verification.isActiveAt(EXPIRES_AT.plusNanos(1)));
    }

    @Test
    void consumesActiveVerification() {
        EmailVerification verification = createVerification();
        Instant consumedAt = CREATED_AT.plusSeconds(60);

        verification.consume(consumedAt);

        assertEquals(consumedAt, verification.consumedAt().orElseThrow());
        assertFalse(verification.isActiveAt(consumedAt));
    }

    @Test
    void cannotConsumeVerificationTwice() {
        EmailVerification verification = createVerification();
        Instant consumedAt = CREATED_AT.plusSeconds(60);
        verification.consume(consumedAt);

        assertThrows(
                InvalidEmailVerificationException.class,
                () -> verification.consume(consumedAt.plusSeconds(1))
        );
    }

    @Test
    void cannotConsumeInvalidatedVerification() {
        EmailVerification verification = createVerification();
        Instant invalidatedAt = CREATED_AT.plusSeconds(60);
        verification.invalidate(invalidatedAt);

        assertThrows(
                InvalidEmailVerificationException.class,
                () -> verification.consume(invalidatedAt.plusSeconds(1))
        );
    }

    @Test
    void cannotConsumeExpiredVerification() {
        EmailVerification verification = createVerification();

        assertThrows(
                InvalidEmailVerificationException.class,
                () -> verification.consume(EXPIRES_AT)
        );
    }

    @Test
    void invalidatesUnusedVerification() {
        EmailVerification verification = createVerification();
        Instant invalidatedAt = CREATED_AT.plusSeconds(60);

        verification.invalidate(invalidatedAt);

        assertEquals(invalidatedAt, verification.invalidatedAt().orElseThrow());
        assertFalse(verification.isActiveAt(invalidatedAt));
    }

    @Test
    void canRecordInvalidationAfterExpiration() {
        EmailVerification verification = createVerification();
        Instant invalidatedAt = EXPIRES_AT.plusSeconds(60);

        verification.invalidate(invalidatedAt);

        assertEquals(invalidatedAt, verification.invalidatedAt().orElseThrow());
    }

    @Test
    void cannotInvalidateVerificationTwice() {
        EmailVerification verification = createVerification();
        Instant invalidatedAt = CREATED_AT.plusSeconds(60);
        verification.invalidate(invalidatedAt);

        assertThrows(
                InvalidEmailVerificationException.class,
                () -> verification.invalidate(invalidatedAt.plusSeconds(1))
        );
    }

    @Test
    void cannotInvalidateConsumedVerification() {
        EmailVerification verification = createVerification();
        Instant consumedAt = CREATED_AT.plusSeconds(60);
        verification.consume(consumedAt);

        assertThrows(
                InvalidEmailVerificationException.class,
                () -> verification.invalidate(consumedAt.plusSeconds(1))
        );
    }

    @Test
    void reconstitutesConsumedVerification() {
        Instant consumedAt = CREATED_AT.plusSeconds(60);

        EmailVerification verification = EmailVerification.reconstitute(
                VERIFICATION_ID,
                USER_ID,
                TARGET_EMAIL,
                TOKEN_HASH,
                VerificationPurpose.REGISTRATION,
                CREATED_AT,
                EXPIRES_AT,
                consumedAt,
                null
        );

        assertEquals(consumedAt, verification.consumedAt().orElseThrow());
        assertFalse(verification.isActiveAt(consumedAt.plusSeconds(1)));
    }

    @Test
    void rejectsExpirationNotAfterCreation() {
        assertThrows(
                InvalidEmailVerificationException.class,
                () -> EmailVerification.create(
                        VERIFICATION_ID,
                        USER_ID,
                        TARGET_EMAIL,
                        TOKEN_HASH,
                        VerificationPurpose.REGISTRATION,
                        CREATED_AT,
                        CREATED_AT
                )
        );
    }

    @Test
    void rejectsStateThatIsBothConsumedAndInvalidated() {
        Instant terminalTime = CREATED_AT.plusSeconds(60);

        assertThrows(
                InvalidEmailVerificationException.class,
                () -> EmailVerification.reconstitute(
                        VERIFICATION_ID,
                        USER_ID,
                        TARGET_EMAIL,
                        TOKEN_HASH,
                        VerificationPurpose.REGISTRATION,
                        CREATED_AT,
                        EXPIRES_AT,
                        terminalTime,
                        terminalTime
                )
        );
    }

    @Test
    void rejectsConsumedStateOutsideValidityPeriod() {
        assertThrows(
                InvalidEmailVerificationException.class,
                () -> EmailVerification.reconstitute(
                        VERIFICATION_ID,
                        USER_ID,
                        TARGET_EMAIL,
                        TOKEN_HASH,
                        VerificationPurpose.REGISTRATION,
                        CREATED_AT,
                        EXPIRES_AT,
                        EXPIRES_AT,
                        null
                )
        );
    }

    @Test
    void rejectsMissingRequiredData() {
        assertThrows(
                InvalidEmailVerificationException.class,
                () -> EmailVerification.create(
                        null,
                        USER_ID,
                        TARGET_EMAIL,
                        TOKEN_HASH,
                        VerificationPurpose.REGISTRATION,
                        CREATED_AT,
                        EXPIRES_AT
                )
        );
        assertThrows(
                InvalidEmailVerificationException.class,
                () -> EmailVerification.create(
                        VERIFICATION_ID,
                        USER_ID,
                        TARGET_EMAIL,
                        TOKEN_HASH,
                        null,
                        CREATED_AT,
                        EXPIRES_AT
                )
        );
    }

    private static EmailVerification createVerification() {
        return EmailVerification.create(
                VERIFICATION_ID,
                USER_ID,
                TARGET_EMAIL,
                TOKEN_HASH,
                VerificationPurpose.REGISTRATION,
                CREATED_AT,
                EXPIRES_AT
        );
    }
}
