package io.github.edtechdevelopment.identity.infrastructure.security.token;

import io.github.edtechdevelopment.identity.application.model.IssuedRefreshToken;
import io.github.edtechdevelopment.identity.infrastructure.security.configuration.IdentityTokenProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecureRefreshTokenIssuerTest {

    private final SecureRefreshTokenIssuer tokenIssuer = new SecureRefreshTokenIssuer(tokenProperties());

    @Test
    void issuesUrlSafeTokenWithRequestedExpirationTime() {
        Instant expiresAt = Instant.parse("2026-10-22T12:00:00Z");

        IssuedRefreshToken token = tokenIssuer.issue(expiresAt);

        assertEquals(43, token.value().length());
        assertTrue(token.value().matches("[A-Za-z0-9_-]+"));
        assertEquals(expiresAt, token.expiresAt());
    }

    @Test
    void issuesDifferentTokens() {
        Instant expiresAt = Instant.parse("2026-10-22T12:00:00Z");

        assertNotEquals(tokenIssuer.issue(expiresAt).value(), tokenIssuer.issue(expiresAt).value());
    }

    private static IdentityTokenProperties tokenProperties() {
        return new IdentityTokenProperties(
                Duration.ofMinutes(5),
                32,
                Duration.ofMinutes(15),
                Duration.ofDays(30),
                32,
                "edtech-backend",
                "edtech-api"
        );
    }
}
