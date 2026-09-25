package io.github.edtechdevelopment.identity.presentation.auth.model.response;

import java.util.Objects;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {

    public TokenResponse {
        Objects.requireNonNull(accessToken, "Access token must not be null");
        Objects.requireNonNull(tokenType, "Token type must not be null");
        if (accessToken.isBlank()) {
            throw new IllegalArgumentException("Access token must not be blank");
        }
        if (tokenType.isBlank()) {
            throw new IllegalArgumentException("Token type must not be blank");
        }
        if (expiresInSeconds <= 0) {
            throw new IllegalArgumentException("Access token lifetime must be positive");
        }
    }

    @Override
    public String toString() {
        return "TokenResponse[tokenType=" + tokenType + ", expiresInSeconds=" + expiresInSeconds + ']';
    }
}
