package io.github.edtechdevelopment.identity.application.port.out.persistence;

import io.github.edtechdevelopment.identity.domain.verification.model.EmailVerification;
import io.github.edtechdevelopment.identity.domain.verification.model.VerificationPurpose;
import io.github.edtechdevelopment.identity.domain.verification.model.VerificationTokenHash;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationRepository {

    Optional<EmailVerification> findActiveByTokenHash(
            VerificationTokenHash tokenHash,
            Instant now
    );

    Optional<EmailVerification> findActiveByTokenHashForUpdate(
            VerificationTokenHash tokenHash,
            Instant now
    );

    void save(EmailVerification verification);

    void invalidateActiveForUser(
            UUID userId,
            VerificationPurpose purpose,
            Instant invalidatedAt
    );
}
