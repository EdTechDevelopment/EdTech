package io.github.edtechdevelopment.identity.application.model;

import java.util.Objects;

public record RefreshSession(
        IssuedRefreshToken token,
        RefreshTokenState state
) {

    public RefreshSession {
        Objects.requireNonNull(token, "Issued refresh token must not be null");
        Objects.requireNonNull(state, "Refresh token state must not be null");
        if (!token.expiresAt().equals(state.expiresAt())) {
            throw new IllegalArgumentException("Refresh token and its state must have the same expiration time");
        }
    }

    @Override
    public String toString() {
        return "RefreshSession[PROTECTED]";
    }
}
