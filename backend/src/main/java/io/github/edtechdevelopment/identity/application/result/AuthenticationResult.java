package io.github.edtechdevelopment.identity.application.result;

import io.github.edtechdevelopment.identity.application.model.IssuedAccessToken;
import io.github.edtechdevelopment.identity.application.model.IssuedRefreshToken;

import java.util.Objects;

public record AuthenticationResult(
        IssuedAccessToken accessToken,
        IssuedRefreshToken refreshToken
) {

    public AuthenticationResult {
        Objects.requireNonNull(accessToken, "Issued access token must not be null");
        Objects.requireNonNull(refreshToken, "Issued refresh token must not be null");
    }

    @Override
    public String toString() {
        return "AuthenticationResult[PROTECTED]";
    }
}
