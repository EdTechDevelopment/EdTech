package io.github.edtechdevelopment.identity.infrastructure.persistence.mapper;

import io.github.edtechdevelopment.identity.domain.user.exception.InvalidEmailException;
import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.verification.exception.InvalidEmailVerificationException;
import io.github.edtechdevelopment.identity.domain.verification.model.EmailVerification;
import io.github.edtechdevelopment.identity.domain.verification.model.VerificationPurpose;
import io.github.edtechdevelopment.identity.domain.verification.model.VerificationTokenHash;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityEmailVerificationsRecord;
import io.github.edtechdevelopment.identity.infrastructure.persistence.exception.InvalidPersistenceDataException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Component
public final class EmailVerificationPersistenceMapper {

    public EmailVerification toDomain(IdentityEmailVerificationsRecord record) {
        Objects.requireNonNull(record, "Email verification record must not be null");

        try {
            return EmailVerification.reconstitute(
                    requireValue(record.getId(), "Verification id must not be null"),
                    requireValue(record.getUserId(), "Verification user id must not be null"),
                    new Email(record.getTargetEmail()),
                    new VerificationTokenHash(record.getTokenHash()),
                    toPurpose(record.getPurpose()),
                    toRequiredInstant(record.getCreatedAt(), "Verification creation time must not be null"),
                    toRequiredInstant(record.getExpiresAt(), "Verification expiration time must not be null"),
                    toInstant(record.getConsumedAt()),
                    toInstant(record.getInvalidatedAt())
            );
        } catch (InvalidEmailException | InvalidEmailVerificationException exception) {
            throw new InvalidPersistenceDataException(
                    "Cannot reconstitute email verification from persistence data",
                    exception
            );
        }
    }

    public IdentityEmailVerificationsRecord toPersistence(EmailVerification verification) {
        Objects.requireNonNull(verification, "Email verification must not be null");

        return new IdentityEmailVerificationsRecord(
                verification.id(),
                verification.userId(),
                verification.targetEmail().value(),
                verification.tokenHash().value(),
                verification.purpose().name(),
                toOffsetDateTime(verification.expiresAt()),
                toOffsetDateTime(verification.consumedAt().orElse(null)),
                toOffsetDateTime(verification.invalidatedAt().orElse(null)),
                toOffsetDateTime(verification.createdAt())
        );
    }

    private static VerificationPurpose toPurpose(String value) {
        requireValue(value, "Verification purpose must not be null");
        try {
            return VerificationPurpose.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidPersistenceDataException(
                    "Unknown verification purpose: " + value,
                    exception
            );
        }
    }

    private static Instant toRequiredInstant(OffsetDateTime value, String message) {
        return requireValue(value, message).toInstant();
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime toOffsetDateTime(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static <T> T requireValue(T value, String message) {
        if (value == null) {
            throw new InvalidPersistenceDataException(message);
        }
        return value;
    }
}
