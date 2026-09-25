package io.github.edtechdevelopment.identity.infrastructure.security.token;

import io.github.edtechdevelopment.identity.application.model.IssuedRefreshToken;
import io.github.edtechdevelopment.identity.application.port.out.security.RefreshTokenIssuer;
import io.github.edtechdevelopment.identity.infrastructure.security.configuration.IdentityTokenProperties;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

@Component
public final class SecureRefreshTokenIssuer implements RefreshTokenIssuer {

    private final SecureRandom secureRandom;
    private final int entropyBytes;

    public SecureRefreshTokenIssuer(IdentityTokenProperties properties) {
        Objects.requireNonNull(properties, "Identity token properties must not be null");
        secureRandom = new SecureRandom();
        entropyBytes = properties.refreshEntropyBytes();
    }

    @Override
    public IssuedRefreshToken issue(Instant expiresAt) {
        Objects.requireNonNull(expiresAt, "Refresh token expiration time must not be null");

        byte[] randomBytes = new byte[entropyBytes];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        return new IssuedRefreshToken(rawToken, expiresAt);
    }
}
