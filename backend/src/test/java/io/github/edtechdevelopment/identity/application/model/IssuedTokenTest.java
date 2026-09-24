package io.github.edtechdevelopment.identity.application.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IssuedTokenTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-09-22T12:30:00Z");

    @Test
    void doesNotExposeAccessTokenThroughToString() {
        IssuedAccessToken token = new IssuedAccessToken("secret-access-token", EXPIRES_AT);

        assertFalse(token.toString().contains(token.value()));
    }

    @Test
    void doesNotExposeRefreshTokenThroughToString() {
        IssuedRefreshToken token = new IssuedRefreshToken("secret-refresh-token", EXPIRES_AT);

        assertFalse(token.toString().contains(token.value()));
    }

    @Test
    void rejectsBlankTokenValues() {
        assertThrows(IllegalArgumentException.class, () -> new IssuedAccessToken(" ", EXPIRES_AT));
        assertThrows(IllegalArgumentException.class, () -> new IssuedRefreshToken(" ", EXPIRES_AT));
    }
}
