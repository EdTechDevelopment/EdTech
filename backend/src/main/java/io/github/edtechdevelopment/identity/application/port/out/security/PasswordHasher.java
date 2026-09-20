package io.github.edtechdevelopment.identity.application.port.out.security;

import io.github.edtechdevelopment.identity.domain.user.model.PasswordHash;

public interface PasswordHasher {

    PasswordHash hash(String rawPassword);

    boolean matches(String rawPassword, PasswordHash passwordHash);
}
