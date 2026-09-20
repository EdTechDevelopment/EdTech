package io.github.edtechdevelopment.identity.infrastructure.persistence.mapper;

import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.verification.model.EmailVerification;
import io.github.edtechdevelopment.identity.domain.verification.model.VerificationPurpose;
import io.github.edtechdevelopment.identity.domain.verification.model.VerificationTokenHash;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityEmailVerificationsRecord;
import io.github.edtechdevelopment.identity.infrastructure.persistence.exception.InvalidPersistenceDataException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailVerificationPersistenceMapperTest {

    private static final UUID VERIFICATION_ID = UUID.fromString("dca19e9b-ef04-4229-ad37-9832be0044bf");
    private static final UUID USER_ID = UUID.fromString("56ecb4cb-3477-4ec1-a4ad-20b05c94bf29");
    private static final Instant CREATED_AT = Instant.parse("2026-09-16T08:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-16T08:05:00Z");
    private static final Instant CONSUMED_AT = Instant.parse("2026-09-16T08:02:00Z");
    private static final VerificationTokenHash TOKEN_HASH = new VerificationTokenHash("a".repeat(64));

    private final EmailVerificationPersistenceMapper mapper = new EmailVerificationPersistenceMapper();

    @Test
    void mapsEmailVerificationToPersistenceDataAndBack() {
        EmailVerification original = EmailVerification.create(
                VERIFICATION_ID,
                USER_ID,
                new Email("anna@example.com"),
                TOKEN_HASH,
                VerificationPurpose.REGISTRATION,
                CREATED_AT,
                EXPIRES_AT
        );

        IdentityEmailVerificationsRecord record = mapper.toPersistence(original);
        EmailVerification restored = mapper.toDomain(record);

        assertAll(
                () -> assertEquals(original.id(), restored.id()),
                () -> assertEquals(original.userId(), restored.userId()),
                () -> assertEquals(original.targetEmail(), restored.targetEmail()),
                () -> assertEquals(original.tokenHash(), restored.tokenHash()),
                () -> assertEquals(original.purpose(), restored.purpose()),
                () -> assertEquals(original.createdAt(), restored.createdAt()),
                () -> assertEquals(original.expiresAt(), restored.expiresAt()),
                () -> assertTrue(restored.consumedAt().isEmpty()),
                () -> assertTrue(restored.invalidatedAt().isEmpty())
        );
    }

    @Test
    void preservesConsumedState() {
        EmailVerification verification = EmailVerification.create(
                VERIFICATION_ID,
                USER_ID,
                new Email("anna@example.com"),
                TOKEN_HASH,
                VerificationPurpose.REGISTRATION,
                CREATED_AT,
                EXPIRES_AT
        );
        verification.consume(CONSUMED_AT);

        EmailVerification restored = mapper.toDomain(mapper.toPersistence(verification));

        assertEquals(CONSUMED_AT, restored.consumedAt().orElseThrow());
        assertTrue(restored.invalidatedAt().isEmpty());
    }

    @Test
    void rejectsUnknownPurposeFromPersistence() {
        IdentityEmailVerificationsRecord record = new IdentityEmailVerificationsRecord(
                VERIFICATION_ID,
                USER_ID,
                "anna@example.com",
                TOKEN_HASH.value(),
                "UNKNOWN",
                toOffsetDateTime(EXPIRES_AT),
                null,
                null,
                toOffsetDateTime(CREATED_AT)
        );

        assertThrows(InvalidPersistenceDataException.class, () -> mapper.toDomain(record));
    }

    private static OffsetDateTime toOffsetDateTime(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
