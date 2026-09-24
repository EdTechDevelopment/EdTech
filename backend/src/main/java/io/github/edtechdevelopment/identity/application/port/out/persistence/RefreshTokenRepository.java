package io.github.edtechdevelopment.identity.application.port.out.persistence;

import io.github.edtechdevelopment.identity.application.model.RefreshTokenState;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    Optional<RefreshTokenState> findByTokenHash(String tokenHash);

    Optional<RefreshTokenState> findByTokenHashForUpdate(String tokenHash);

    void save(RefreshTokenState token);

    void revoke(UUID tokenId, Instant revokedAt);

    void revokeFamily(UUID userId, UUID familyId, Instant revokedAt);
}
