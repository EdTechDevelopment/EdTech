package io.github.edtechdevelopment.identity.infrastructure.persistence.mapper;

import io.github.edtechdevelopment.identity.application.model.RefreshTokenState;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityRefreshTokensRecord;
import io.github.edtechdevelopment.identity.infrastructure.persistence.exception.InvalidPersistenceDataException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefreshTokenPersistenceMapperTest {

    private static final UUID TOKEN_ID = UUID.fromString("5f2e3394-49da-4652-81c2-b40af8d8fd91");
    private static final UUID USER_ID = UUID.fromString("2bfdc524-e209-4c39-8b29-abd3488b95c9");
    private static final UUID FAMILY_ID = UUID.fromString("d06856a2-62c0-4961-9573-1f43cfce9a58");
    private static final String TOKEN_HASH = "abcdef0123456789".repeat(4);
    private static final Instant CREATED_AT = Instant.parse("2026-09-22T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-22T10:00:00Z");

    private final RefreshTokenPersistenceMapper mapper = new RefreshTokenPersistenceMapper();

    @Test
    void mapsRefreshTokenToPersistenceDataAndBack() {
        RefreshTokenState original = tokenState(null);

        IdentityRefreshTokensRecord record = mapper.toPersistence(original);
        RefreshTokenState restored = mapper.toApplication(record);

        assertAll(
                () -> assertEquals(original, restored),
                () -> assertEquals(TOKEN_ID, record.getId()),
                () -> assertEquals(USER_ID, record.getUserId()),
                () -> assertEquals(TOKEN_HASH, record.getTokenHash()),
                () -> assertEquals(FAMILY_ID, record.getFamilyId()),
                () -> assertNull(record.getRevokedAt())
        );
    }

    @Test
    void preservesRevocationTime() {
        Instant revokedAt = CREATED_AT.plusSeconds(60);

        RefreshTokenState restored = mapper.toApplication(mapper.toPersistence(tokenState(revokedAt)));

        assertEquals(revokedAt, restored.revokedAt());
    }

    @Test
    void rejectsInvalidHashFromPersistence() {
        IdentityRefreshTokensRecord record = new IdentityRefreshTokensRecord(
                TOKEN_ID,
                USER_ID,
                "invalid-hash",
                FAMILY_ID,
                toOffsetDateTime(EXPIRES_AT),
                null,
                toOffsetDateTime(CREATED_AT)
        );

        assertThrows(InvalidPersistenceDataException.class, () -> mapper.toApplication(record));
    }

    private static RefreshTokenState tokenState(Instant revokedAt) {
        return new RefreshTokenState(
                TOKEN_ID,
                USER_ID,
                TOKEN_HASH,
                FAMILY_ID,
                EXPIRES_AT,
                revokedAt,
                CREATED_AT
        );
    }

    private static OffsetDateTime toOffsetDateTime(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
