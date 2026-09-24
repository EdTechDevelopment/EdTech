package io.github.edtechdevelopment.identity.application.model;

import java.time.Instant;
import java.util.Objects;

public record IssuedAccessToken(String value, Instant expiresAt) {

    public IssuedAccessToken {
        value = requireTokenValue(value, "Access token");
        Objects.requireNonNull(expiresAt, "Access token expiration time must not be null");
    }

    @Override
    public String toString() {
        return "IssuedAccessToken[expiresAt=" + expiresAt + ']';
    }

    private static String requireTokenValue(String value, String tokenName) {
        Objects.requireNonNull(value, tokenName + " value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(tokenName + " value must not be blank");
        }
        return value;
    }
}
