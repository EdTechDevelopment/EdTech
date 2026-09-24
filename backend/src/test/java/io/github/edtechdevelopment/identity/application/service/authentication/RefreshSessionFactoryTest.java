package io.github.edtechdevelopment.identity.application.service.authentication;

import io.github.edtechdevelopment.identity.application.model.IssuedRefreshToken;
import io.github.edtechdevelopment.identity.application.model.RefreshSession;
import io.github.edtechdevelopment.identity.application.port.out.security.RefreshTokenHasher;
import io.github.edtechdevelopment.identity.application.port.out.security.RefreshTokenIssuer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RefreshSessionFactoryTest {

    @Test
    void createsNewRefreshFamilyAndStoresOnlyTheTokenHashInState() {
        RefreshTokenIssuer tokenIssuer = mock(RefreshTokenIssuer.class);
        RefreshTokenHasher tokenHasher = mock(RefreshTokenHasher.class);
        Instant issuedAt = Instant.parse("2026-09-24T12:00:00Z");
        Instant expiresAt = Instant.parse("2026-10-24T12:00:00Z");
        String rawToken = "raw-refresh-token";
        String tokenHash = "a".repeat(64);
        UUID userId = UUID.fromString("31bc2bca-c542-4518-ae63-5ae093beed5e");
        when(tokenIssuer.issue(expiresAt)).thenReturn(new IssuedRefreshToken(rawToken, expiresAt));
        when(tokenHasher.hash(rawToken)).thenReturn(tokenHash);

        RefreshSessionFactory factory = new RefreshSessionFactory(
                tokenIssuer,
                tokenHasher,
                Duration.ofDays(30)
        );

        RefreshSession session = factory.create(userId, issuedAt);

        assertEquals(rawToken, session.token().value());
        assertEquals(tokenHash, session.state().tokenHash());
        assertNotEquals(rawToken, session.state().tokenHash());
        assertEquals(userId, session.state().userId());
        assertEquals(issuedAt, session.state().createdAt());
        assertEquals(expiresAt, session.state().expiresAt());
        assertNotNull(session.state().id());
        assertNotNull(session.state().familyId());
        assertFalse(session.state().isRevoked());
    }

    @Test
    void rotatesTokenWithoutChangingFamilyOrExpiration() {
        RefreshTokenIssuer tokenIssuer = mock(RefreshTokenIssuer.class);
        RefreshTokenHasher tokenHasher = mock(RefreshTokenHasher.class);
        Instant rotatedAt = Instant.parse("2026-09-25T12:00:00Z");
        Instant familyExpiresAt = Instant.parse("2026-10-24T12:00:00Z");
        String rawToken = "rotated-raw-refresh-token";
        String tokenHash = "b".repeat(64);
        UUID userId = UUID.fromString("31bc2bca-c542-4518-ae63-5ae093beed5e");
        UUID familyId = UUID.fromString("80e24857-4467-4f6f-86cb-4d91343bf965");
        when(tokenIssuer.issue(familyExpiresAt)).thenReturn(new IssuedRefreshToken(rawToken, familyExpiresAt));
        when(tokenHasher.hash(rawToken)).thenReturn(tokenHash);
        RefreshSessionFactory factory = new RefreshSessionFactory(
                tokenIssuer,
                tokenHasher,
                Duration.ofDays(30)
        );

        RefreshSession session = factory.rotate(userId, familyId, familyExpiresAt, rotatedAt);

        assertEquals(familyId, session.state().familyId());
        assertEquals(familyExpiresAt, session.state().expiresAt());
        assertEquals(rotatedAt, session.state().createdAt());
        assertEquals(tokenHash, session.state().tokenHash());
    }
}
