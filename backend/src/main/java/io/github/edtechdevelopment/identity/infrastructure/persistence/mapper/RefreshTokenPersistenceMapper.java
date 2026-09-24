package io.github.edtechdevelopment.identity.infrastructure.persistence.mapper;

import io.github.edtechdevelopment.identity.application.model.RefreshTokenState;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityRefreshTokensRecord;
import io.github.edtechdevelopment.identity.infrastructure.persistence.exception.InvalidPersistenceDataException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Component
public final class RefreshTokenPersistenceMapper {

    public RefreshTokenState toApplication(IdentityRefreshTokensRecord record) {
        Objects.requireNonNull(record, "Refresh token record must not be null");

        try {
            return new RefreshTokenState(
                    requireValue(record.getId(), "Refresh token id must not be null"),
                    requireValue(record.getUserId(), "Refresh token user id must not be null"),
                    requireValue(record.getTokenHash(), "Refresh token hash must not be null"),
                    requireValue(record.getFamilyId(), "Refresh token family id must not be null"),
                    toRequiredInstant(record.getExpiresAt(), "Refresh token expiration time must not be null"),
                    toInstant(record.getRevokedAt()),
                    toRequiredInstant(record.getCreatedAt(), "Refresh token creation time must not be null")
            );
        } catch (IllegalArgumentException exception) {
            throw new InvalidPersistenceDataException(
                    "Cannot reconstitute refresh token from persistence data",
                    exception
            );
        }
    }

    public IdentityRefreshTokensRecord toPersistence(RefreshTokenState token) {
        Objects.requireNonNull(token, "Refresh token state must not be null");

        return new IdentityRefreshTokensRecord(
                token.id(),
                token.userId(),
                token.tokenHash(),
                token.familyId(),
                toOffsetDateTime(token.expiresAt()),
                toOffsetDateTime(token.revokedAt()),
                toOffsetDateTime(token.createdAt())
        );
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
