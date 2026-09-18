package io.github.edtechdevelopment.identity.infrastructure.security.password;

import io.github.edtechdevelopment.identity.application.port.out.security.PasswordHasher;
import io.github.edtechdevelopment.identity.domain.user.model.PasswordHash;
import io.github.edtechdevelopment.identity.infrastructure.security.configuration.IdentityPasswordProperties;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Component
public final class BCryptPasswordHasher implements PasswordHasher {

    private static final int MAX_PASSWORD_BYTES = 72;

    private final BCryptPasswordEncoder passwordEncoder;

    public BCryptPasswordHasher(IdentityPasswordProperties properties) {
        Objects.requireNonNull(properties, "Identity password properties must not be null");
        passwordEncoder = new BCryptPasswordEncoder(properties.bcryptStrength());
    }

    @Override
    public PasswordHash hash(String rawPassword) {
        requireSupportedPassword(rawPassword);
        return new PasswordHash(passwordEncoder.encode(rawPassword));
    }

    @Override
    public boolean matches(String rawPassword, PasswordHash passwordHash) {
        requireSupportedPassword(rawPassword);
        Objects.requireNonNull(passwordHash, "Password hash must not be null");
        return passwordEncoder.matches(rawPassword, passwordHash.value());
    }

    private static void requireSupportedPassword(String rawPassword) {
        Objects.requireNonNull(rawPassword, "Raw password must not be null");
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException("BCrypt password must not exceed 72 bytes");
        }
    }
}
