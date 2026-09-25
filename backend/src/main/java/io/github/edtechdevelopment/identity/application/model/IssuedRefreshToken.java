package io.github.edtechdevelopment.identity.application.model;

import java.time.Instant;
import java.util.Objects;

public record IssuedRefreshToken(String value, Instant expiresAt) {

    public IssuedRefreshToken {
        value = requireTokenValue(value, "Refresh token");
        Objects.requireNonNull(expiresAt, "Refresh token expiration time must not be null");
    }

    @Override
    public String toString() {
        return "IssuedRefreshToken[expiresAt=" + expiresAt + ']';
    }

    private static String requireTokenValue(String value, String tokenName) {
        Objects.requireNonNull(value, tokenName + " value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(tokenName + " value must not be blank");
        }
        return value;
    }
}
