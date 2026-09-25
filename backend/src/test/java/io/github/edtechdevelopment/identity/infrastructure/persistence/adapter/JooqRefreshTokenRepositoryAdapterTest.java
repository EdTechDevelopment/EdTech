package io.github.edtechdevelopment.identity.infrastructure.persistence.adapter;

import io.github.edtechdevelopment.identity.application.model.RefreshTokenState;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityRefreshTokensRecord;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.repository.RefreshTokenJooqRepository;
import io.github.edtechdevelopment.identity.infrastructure.persistence.mapper.RefreshTokenPersistenceMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JooqRefreshTokenRepositoryAdapterTest {

    private static final UUID TOKEN_ID = UUID.fromString("873b8163-076d-4908-8585-07e2fa391a66");
    private static final UUID USER_ID = UUID.fromString("ac341855-2153-44a7-bd43-962f132cfb03");
    private static final UUID FAMILY_ID = UUID.fromString("70175044-1be0-44a4-8c3a-85116867d50e");
    private static final String TOKEN_HASH = "1234567890abcdef".repeat(4);
    private static final Instant CREATED_AT = Instant.parse("2026-09-22T11:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-22T11:00:00Z");
    private static final Instant REVOKED_AT = Instant.parse("2026-09-22T11:05:00Z");

    private final RefreshTokenJooqRepository repository = mock(RefreshTokenJooqRepository.class);
    private final RefreshTokenPersistenceMapper mapper = new RefreshTokenPersistenceMapper();
    private final JooqRefreshTokenRepositoryAdapter adapter =
            new JooqRefreshTokenRepositoryAdapter(repository, mapper);

    @Test
    void findsAndMapsRefreshTokenWithoutLock() {
        RefreshTokenState token = tokenState(REVOKED_AT);
        when(repository.findByTokenHash(TOKEN_HASH))
                .thenReturn(Optional.of(mapper.toPersistence(token)));

        RefreshTokenState found = adapter.findByTokenHash(TOKEN_HASH).orElseThrow();

        assertEquals(token, found);
    }

    @Test
    void findsAndMapsRefreshTokenForUpdate() {
        RefreshTokenState token = tokenState(REVOKED_AT);
        when(repository.findByTokenHashForUpdate(TOKEN_HASH))
                .thenReturn(Optional.of(mapper.toPersistence(token)));

        RefreshTokenState found = adapter.findByTokenHashForUpdate(TOKEN_HASH).orElseThrow();

        assertEquals(token, found);
    }

    @Test
    void mapsAndSavesRefreshToken() {
        adapter.save(tokenState(null));

        ArgumentCaptor<IdentityRefreshTokensRecord> recordCaptor =
                ArgumentCaptor.forClass(IdentityRefreshTokensRecord.class);
        verify(repository).save(recordCaptor.capture());
        assertEquals(TOKEN_ID, recordCaptor.getValue().getId());
        assertEquals(TOKEN_HASH, recordCaptor.getValue().getTokenHash());
    }

    @Test
    void delegatesSingleTokenRevocation() {
        adapter.revoke(TOKEN_ID, REVOKED_AT);

        verify(repository).revoke(TOKEN_ID, toOffsetDateTime(REVOKED_AT));
    }

    @Test
    void delegatesFamilyRevocationWithOwnerScope() {
        adapter.revokeFamily(USER_ID, FAMILY_ID, REVOKED_AT);

        verify(repository).revokeFamily(USER_ID, FAMILY_ID, toOffsetDateTime(REVOKED_AT));
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
