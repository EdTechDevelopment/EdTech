package io.github.edtechdevelopment.identity.infrastructure.persistence.adapter;

import io.github.edtechdevelopment.identity.application.port.out.persistence.EmailVerificationRepository;
import io.github.edtechdevelopment.identity.domain.verification.model.EmailVerification;
import io.github.edtechdevelopment.identity.domain.verification.model.VerificationPurpose;
import io.github.edtechdevelopment.identity.domain.verification.model.VerificationTokenHash;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityEmailVerificationsRecord;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.repository.EmailVerificationJooqRepository;
import io.github.edtechdevelopment.identity.infrastructure.persistence.mapper.EmailVerificationPersistenceMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JooqEmailVerificationRepositoryAdapter implements EmailVerificationRepository {

    private final EmailVerificationJooqRepository repository;
    private final EmailVerificationPersistenceMapper mapper;

    public JooqEmailVerificationRepositoryAdapter(
            EmailVerificationJooqRepository repository,
            EmailVerificationPersistenceMapper mapper
    ) {
        this.repository = Objects.requireNonNull(
                repository,
                "Email verification jOOQ repository must not be null"
        );
        this.mapper = Objects.requireNonNull(
                mapper,
                "Email verification persistence mapper must not be null"
        );
    }

    @Override
    public Optional<EmailVerification> findActiveByTokenHash(
            VerificationTokenHash tokenHash,
            Instant now
    ) {
        Objects.requireNonNull(tokenHash, "Verification token hash must not be null");
        Objects.requireNonNull(now, "Verification check time must not be null");

        return repository
                .findActiveByTokenHash(tokenHash.value(), toOffsetDateTime(now))
                .map(mapper::toDomain);
    }

    @Override
    public Optional<EmailVerification> findActiveByTokenHashForUpdate(
            VerificationTokenHash tokenHash,
            Instant now
    ) {
        Objects.requireNonNull(tokenHash, "Verification token hash must not be null");
        Objects.requireNonNull(now, "Verification check time must not be null");

        return repository
                .findActiveByTokenHashForUpdate(tokenHash.value(), toOffsetDateTime(now))
                .map(mapper::toDomain);
    }

    @Override
    public void save(EmailVerification verification) {
        Objects.requireNonNull(verification, "Email verification must not be null");
        IdentityEmailVerificationsRecord record = mapper.toPersistence(verification);
        repository.save(record);
    }

    @Override
    public void invalidateActiveForUser(
            UUID userId,
            VerificationPurpose purpose,
            Instant invalidatedAt
    ) {
        Objects.requireNonNull(userId, "User id must not be null");
        Objects.requireNonNull(purpose, "Verification purpose must not be null");
        Objects.requireNonNull(invalidatedAt, "Invalidation time must not be null");

        repository.invalidateActiveForUser(
                userId,
                purpose.name(),
                toOffsetDateTime(invalidatedAt)
        );
    }

    private static OffsetDateTime toOffsetDateTime(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
