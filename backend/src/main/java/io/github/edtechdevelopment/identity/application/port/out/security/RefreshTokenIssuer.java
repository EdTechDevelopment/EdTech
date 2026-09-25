package io.github.edtechdevelopment.identity.application.port.out.security;

import io.github.edtechdevelopment.identity.application.model.IssuedRefreshToken;

import java.time.Instant;

public interface RefreshTokenIssuer {

    IssuedRefreshToken issue(Instant expiresAt);
}
