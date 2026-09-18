package io.github.edtechdevelopment.identity.infrastructure.persistence.adapter;

import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.verification.model.EmailVerification;
import io.github.edtechdevelopment.identity.domain.verification.model.VerificationPurpose;
import io.github.edtechdevelopment.identity.domain.verification.model.VerificationTokenHash;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.generated.tables.records.IdentityEmailVerificationsRecord;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.repository.EmailVerificationJooqRepository;
import io.github.edtechdevelopment.identity.infrastructure.persistence.mapper.EmailVerificationPersistenceMapper;
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

class JooqEmailVerificationRepositoryAdapterTest {

    private static final UUID VERIFICATION_ID = UUID.fromString("92aa61ec-5022-41be-82d5-b999b2ad22d1");
    private static final UUID USER_ID = UUID.fromString("a9791bdc-1865-4473-8d79-3d64acaec397");
    private static final Instant CREATED_AT = Instant.parse("2026-09-16T09:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-16T09:05:00Z");
    private static final VerificationTokenHash TOKEN_HASH = new VerificationTokenHash("b".repeat(64));

    private final EmailVerificationJooqRepository repository = mock(EmailVerificationJooqRepository.class);
    private final EmailVerificationPersistenceMapper mapper = new EmailVerificationPersistenceMapper();
    private final JooqEmailVerificationRepositoryAdapter adapter =
            new JooqEmailVerificationRepositoryAdapter(repository, mapper);

    @Test
    void findsAndMapsActiveVerification() {
        EmailVerification verification = verification();
        IdentityEmailVerificationsRecord record = mapper.toPersistence(verification);
        when(repository.findActiveByTokenHashForUpdate(
                TOKEN_HASH.value(),
                toOffsetDateTime(CREATED_AT)
        )).thenReturn(Optional.of(record));

        EmailVerification found = adapter
                .findActiveByTokenHashForUpdate(TOKEN_HASH, CREATED_AT)
                .orElseThrow();

        assertEquals(verification.id(), found.id());
        assertEquals(verification.targetEmail(), found.targetEmail());
    }

    @Test
    void mapsAndSavesVerification() {
        EmailVerification verification = verification();

        adapter.save(verification);

        ArgumentCaptor<IdentityEmailVerificationsRecord> recordCaptor =
                ArgumentCaptor.forClass(IdentityEmailVerificationsRecord.class);
        verify(repository).save(recordCaptor.capture());
        assertEquals(VERIFICATION_ID, recordCaptor.getValue().getId());
        assertEquals(TOKEN_HASH.value(), recordCaptor.getValue().getTokenHash());
    }

    @Test
    void delegatesActiveInvalidationUsingPersistenceValues() {
        adapter.invalidateActiveForUser(
                USER_ID,
                VerificationPurpose.REGISTRATION,
                CREATED_AT
        );

        verify(repository).invalidateActiveForUser(
                USER_ID,
                "REGISTRATION",
                toOffsetDateTime(CREATED_AT)
        );
    }

    private static EmailVerification verification() {
        return EmailVerification.create(
                VERIFICATION_ID,
                USER_ID,
                new Email("anna@example.com"),
                TOKEN_HASH,
                VerificationPurpose.REGISTRATION,
                CREATED_AT,
                EXPIRES_AT
        );
    }

    private static OffsetDateTime toOffsetDateTime(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
