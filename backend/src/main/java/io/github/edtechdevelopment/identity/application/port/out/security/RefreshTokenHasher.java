package io.github.edtechdevelopment.identity.application.port.out.security;

public interface RefreshTokenHasher {

    String hash(String rawToken);
}
