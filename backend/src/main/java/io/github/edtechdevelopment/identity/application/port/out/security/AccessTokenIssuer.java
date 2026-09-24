package io.github.edtechdevelopment.identity.application.port.out.security;

import io.github.edtechdevelopment.identity.application.model.IssuedAccessToken;
import io.github.edtechdevelopment.identity.domain.user.model.User;

import java.time.Instant;

public interface AccessTokenIssuer {

    IssuedAccessToken issue(User user, Instant issuedAt);
}
