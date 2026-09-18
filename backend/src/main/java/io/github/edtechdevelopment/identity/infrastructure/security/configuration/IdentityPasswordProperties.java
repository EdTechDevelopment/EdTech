package io.github.edtechdevelopment.identity.infrastructure.security.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity.security.password")
public record IdentityPasswordProperties(int bcryptStrength) {

    private static final int MIN_BCRYPT_STRENGTH = 4;
    private static final int MAX_BCRYPT_STRENGTH = 31;

    public IdentityPasswordProperties {
        if (bcryptStrength < MIN_BCRYPT_STRENGTH || bcryptStrength > MAX_BCRYPT_STRENGTH) {
            throw new IllegalArgumentException("BCrypt strength must be between 4 and 31");
        }
    }
}
