package io.github.edtechdevelopment.identity.application.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record RefreshTokenState(
        UUID id,
        UUID userId,
        String tokenHash,
        UUID familyId,
        Instant expiresAt,
        Instant revokedAt,
        Instant createdAt
) {

    private static final Pattern SHA_256_HEX_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public RefreshTokenState {
        Objects.requireNonNull(id, "Refresh token id must not be null");
        Objects.requireNonNull(userId, "Refresh token user id must not be null");
        Objects.requireNonNull(tokenHash, "Refresh token hash must not be null");
        Objects.requireNonNull(familyId, "Refresh token family id must not be null");
        Objects.requireNonNull(expiresAt, "Refresh token expiration time must not be null");
        Objects.requireNonNull(createdAt, "Refresh token creation time must not be null");

        if (!SHA_256_HEX_PATTERN.matcher(tokenHash).matches()) {
            throw new IllegalArgumentException(
                    "Refresh token hash must contain exactly 64 lowercase hexadecimal characters"
            );
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException(
                    "Refresh token expiration time must be after creation time"
            );
        }
        if (revokedAt != null && revokedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "Refresh token revocation time must not be before creation time"
            );
        }
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpiredAt(Instant checkedAt) {
        Objects.requireNonNull(checkedAt, "Refresh token check time must not be null");
        return !checkedAt.isBefore(expiresAt);
    }

    public boolean isActiveAt(Instant checkedAt) {
        Objects.requireNonNull(checkedAt, "Refresh token check time must not be null");
        return !isRevoked() && !checkedAt.isBefore(createdAt) && !isExpiredAt(checkedAt);
    }
}
