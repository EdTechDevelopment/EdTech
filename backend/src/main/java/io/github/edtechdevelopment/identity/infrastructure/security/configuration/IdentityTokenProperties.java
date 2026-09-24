package io.github.edtechdevelopment.identity.infrastructure.security.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "identity.token")
public record IdentityTokenProperties(
        Duration verificationTtl,
        int verificationEntropyBytes,
        Duration accessTtl,
        Duration refreshFamilyTtl,
        int refreshEntropyBytes,
        String issuer,
        String audience
) {

    private static final int MIN_TOKEN_ENTROPY_BYTES = 32;

    public IdentityTokenProperties {
        requirePositiveDuration(verificationTtl, "Verification token TTL");
        requirePositiveDuration(accessTtl, "Access token TTL");
        requirePositiveDuration(refreshFamilyTtl, "Refresh token family TTL");

        if (verificationEntropyBytes < MIN_TOKEN_ENTROPY_BYTES) {
            throw new IllegalArgumentException("Verification token entropy must be at least 32 bytes");
        }
        if (refreshEntropyBytes < MIN_TOKEN_ENTROPY_BYTES) {
            throw new IllegalArgumentException("Refresh token entropy must be at least 32 bytes");
        }

        issuer = requireText(issuer, "Token issuer");
        audience = requireText(audience, "Token audience");
    }

    private static void requirePositiveDuration(Duration duration, String propertyName) {
        Objects.requireNonNull(duration, propertyName + " must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(propertyName + " must be positive");
        }
    }

    private static String requireText(String value, String propertyName) {
        Objects.requireNonNull(value, propertyName + " must not be null");
        String normalizedValue = value.strip();
        if (normalizedValue.isEmpty()) {
            throw new IllegalArgumentException(propertyName + " must not be blank");
        }
        return normalizedValue;
    }
}
