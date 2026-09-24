package io.github.edtechdevelopment.identity.application.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshTokenStateTest {

    private static final UUID TOKEN_ID = UUID.fromString("9d6c1354-037a-4777-83c6-ef7339a15f18");
    private static final UUID USER_ID = UUID.fromString("ee56a3cd-7e71-41ab-b127-9bbf2fab3f3c");
    private static final UUID FAMILY_ID = UUID.fromString("ac85ea53-a2aa-4285-b65e-a31ff4474104");
    private static final String TOKEN_HASH = "0123456789abcdef".repeat(4);
    private static final Instant CREATED_AT = Instant.parse("2026-09-22T09:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-22T09:00:00Z");

    @Test
    void isActiveOnlyInsideValidityPeriod() {
        RefreshTokenState token = activeToken();

        assertFalse(token.isActiveAt(CREATED_AT.minusNanos(1)));
        assertTrue(token.isActiveAt(CREATED_AT));
        assertTrue(token.isActiveAt(EXPIRES_AT.minusNanos(1)));
        assertFalse(token.isActiveAt(EXPIRES_AT));
        assertTrue(token.isExpiredAt(EXPIRES_AT));
    }

    @Test
    void revokedTokenIsNeverActive() {
        Instant revokedAt = CREATED_AT.plusSeconds(60);
        RefreshTokenState token = new RefreshTokenState(
                TOKEN_ID,
                USER_ID,
                TOKEN_HASH,
                FAMILY_ID,
                EXPIRES_AT,
                revokedAt,
                CREATED_AT
        );

        assertTrue(token.isRevoked());
        assertFalse(token.isActiveAt(revokedAt));
    }

    @Test
    void rejectsNonCanonicalHash() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RefreshTokenState(
                        TOKEN_ID,
                        USER_ID,
                        "A".repeat(64),
                        FAMILY_ID,
                        EXPIRES_AT,
                        null,
                        CREATED_AT
                )
        );
    }

    @Test
    void rejectsExpirationNotAfterCreation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RefreshTokenState(
                        TOKEN_ID,
                        USER_ID,
                        TOKEN_HASH,
                        FAMILY_ID,
                        CREATED_AT,
                        null,
                        CREATED_AT
                )
        );
    }

    @Test
    void rejectsRevocationBeforeCreation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RefreshTokenState(
                        TOKEN_ID,
                        USER_ID,
                        TOKEN_HASH,
                        FAMILY_ID,
                        EXPIRES_AT,
                        CREATED_AT.minusNanos(1),
                        CREATED_AT
                )
        );
    }

    private static RefreshTokenState activeToken() {
        return new RefreshTokenState(
                TOKEN_ID,
                USER_ID,
                TOKEN_HASH,
                FAMILY_ID,
                EXPIRES_AT,
                null,
                CREATED_AT
        );
    }
}
