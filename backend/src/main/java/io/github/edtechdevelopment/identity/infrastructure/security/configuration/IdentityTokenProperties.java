package io.github.edtechdevelopment.identity.infrastructure.security.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "identity.token")
public record IdentityTokenProperties(
        Duration verificationTtl,
        int verificationEntropyBytes
) {

    private static final int MIN_VERIFICATION_ENTROPY_BYTES = 32;

    public IdentityTokenProperties {
        Objects.requireNonNull(verificationTtl, "Verification token TTL must not be null");
        if (verificationTtl.isZero() || verificationTtl.isNegative()) {
            throw new IllegalArgumentException("Verification token TTL must be positive");
        }
        if (verificationEntropyBytes < MIN_VERIFICATION_ENTROPY_BYTES) {
            throw new IllegalArgumentException("Verification token entropy must be at least 32 bytes");
        }
    }
}
