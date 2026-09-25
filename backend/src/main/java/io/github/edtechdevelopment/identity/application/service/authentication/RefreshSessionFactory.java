package io.github.edtechdevelopment.identity.application.service.authentication;

import io.github.edtechdevelopment.identity.application.model.IssuedRefreshToken;
import io.github.edtechdevelopment.identity.application.model.RefreshSession;
import io.github.edtechdevelopment.identity.application.model.RefreshTokenState;
import io.github.edtechdevelopment.identity.application.port.out.security.RefreshTokenHasher;
import io.github.edtechdevelopment.identity.application.port.out.security.RefreshTokenIssuer;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class RefreshSessionFactory {

    private final RefreshTokenIssuer refreshTokenIssuer;
    private final RefreshTokenHasher refreshTokenHasher;
    private final Duration refreshFamilyTtl;

    public RefreshSessionFactory(
            RefreshTokenIssuer refreshTokenIssuer,
            RefreshTokenHasher refreshTokenHasher,
            Duration refreshFamilyTtl
    ) {
        this.refreshTokenIssuer = Objects.requireNonNull(
                refreshTokenIssuer,
                "Refresh token issuer must not be null"
        );
        this.refreshTokenHasher = Objects.requireNonNull(
                refreshTokenHasher,
                "Refresh token hasher must not be null"
        );
        this.refreshFamilyTtl = requirePositiveDuration(refreshFamilyTtl);
    }

    public RefreshSession create(UUID userId, Instant issuedAt) {
        Objects.requireNonNull(userId, "User id must not be null");
        Objects.requireNonNull(issuedAt, "Refresh session issue time must not be null");

        Instant expiresAt = issuedAt.plus(refreshFamilyTtl);
        return createToken(userId, UUID.randomUUID(), expiresAt, issuedAt);
    }

    public RefreshSession rotate(
            UUID userId,
            UUID familyId,
            Instant expiresAt,
            Instant issuedAt
    ) {
        Objects.requireNonNull(userId, "User id must not be null");
        Objects.requireNonNull(familyId, "Refresh token family id must not be null");
        Objects.requireNonNull(expiresAt, "Refresh token expiration time must not be null");
        Objects.requireNonNull(issuedAt, "Refresh token issue time must not be null");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("Rotated refresh token must expire after its issue time");
        }

        return createToken(userId, familyId, expiresAt, issuedAt);
    }

    private RefreshSession createToken(
            UUID userId,
            UUID familyId,
            Instant expiresAt,
            Instant issuedAt
    ) {
        IssuedRefreshToken refreshToken = refreshTokenIssuer.issue(expiresAt);
        RefreshTokenState tokenState = new RefreshTokenState(
                UUID.randomUUID(),
                userId,
                refreshTokenHasher.hash(refreshToken.value()),
                familyId,
                expiresAt,
                null,
                issuedAt
        );
        return new RefreshSession(refreshToken, tokenState);
    }

    private static Duration requirePositiveDuration(Duration duration) {
        Objects.requireNonNull(duration, "Refresh token family TTL must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Refresh token family TTL must be positive");
        }
        return duration;
    }
}
