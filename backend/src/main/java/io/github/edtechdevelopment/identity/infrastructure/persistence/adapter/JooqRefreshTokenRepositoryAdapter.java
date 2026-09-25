package io.github.edtechdevelopment.identity.infrastructure.persistence.adapter;

import io.github.edtechdevelopment.identity.application.model.RefreshTokenState;
import io.github.edtechdevelopment.identity.application.port.out.persistence.RefreshTokenRepository;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityRefreshTokensRecord;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.repository.RefreshTokenJooqRepository;
import io.github.edtechdevelopment.identity.infrastructure.persistence.mapper.RefreshTokenPersistenceMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JooqRefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenJooqRepository repository;
    private final RefreshTokenPersistenceMapper mapper;

    public JooqRefreshTokenRepositoryAdapter(
            RefreshTokenJooqRepository repository,
            RefreshTokenPersistenceMapper mapper
    ) {
        this.repository = Objects.requireNonNull(
                repository,
                "Refresh token jOOQ repository must not be null"
        );
        this.mapper = Objects.requireNonNull(
                mapper,
                "Refresh token persistence mapper must not be null"
        );
    }

    @Override
    public Optional<RefreshTokenState> findByTokenHash(String tokenHash) {
        Objects.requireNonNull(tokenHash, "Refresh token hash must not be null");

        return repository
                .findByTokenHash(tokenHash)
                .map(mapper::toApplication);
    }

    @Override
    public Optional<RefreshTokenState> findByTokenHashForUpdate(String tokenHash) {
        Objects.requireNonNull(tokenHash, "Refresh token hash must not be null");

        return repository
                .findByTokenHashForUpdate(tokenHash)
                .map(mapper::toApplication);
    }

    @Override
    public void save(RefreshTokenState token) {
        Objects.requireNonNull(token, "Refresh token state must not be null");
        IdentityRefreshTokensRecord record = mapper.toPersistence(token);
        repository.save(record);
    }

    @Override
    public void revoke(UUID tokenId, Instant revokedAt) {
        Objects.requireNonNull(tokenId, "Refresh token id must not be null");
        Objects.requireNonNull(revokedAt, "Refresh token revocation time must not be null");
        repository.revoke(tokenId, toOffsetDateTime(revokedAt));
    }

    @Override
    public void revokeFamily(UUID userId, UUID familyId, Instant revokedAt) {
        Objects.requireNonNull(userId, "Refresh token user id must not be null");
        Objects.requireNonNull(familyId, "Refresh token family id must not be null");
        Objects.requireNonNull(revokedAt, "Refresh token family revocation time must not be null");
        repository.revokeFamily(userId, familyId, toOffsetDateTime(revokedAt));
    }

    private static OffsetDateTime toOffsetDateTime(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
