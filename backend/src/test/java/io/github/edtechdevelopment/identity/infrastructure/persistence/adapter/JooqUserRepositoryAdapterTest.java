package io.github.edtechdevelopment.identity.infrastructure.persistence.adapter;

import io.github.edtechdevelopment.identity.application.exception.EmailAlreadyExistsException;
import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.user.model.PasswordHash;
import io.github.edtechdevelopment.identity.domain.user.model.User;
import io.github.edtechdevelopment.identity.domain.user.model.UserRole;
import io.github.edtechdevelopment.identity.domain.user.model.UserStatus;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.model.UserPersistenceData;
import io.github.edtechdevelopment.identity.infrastructure.persistence.data.repository.UserJooqRepository;
import io.github.edtechdevelopment.identity.infrastructure.persistence.mapper.UserPersistenceMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JooqUserRepositoryAdapterTest {

    private static final UUID USER_ID = UUID.fromString("4647e862-0460-4320-8780-b705dcc43278");
    private static final Email EMAIL = new Email("anna@example.com");
    private static final Instant CREATED_AT = Instant.parse("2026-09-15T12:00:00Z");

    private final UserJooqRepository repository = mock(UserJooqRepository.class);
    private final UserPersistenceMapper mapper = new UserPersistenceMapper();
    private final JooqUserRepositoryAdapter adapter = new JooqUserRepositoryAdapter(repository, mapper);

    @Test
    void findsDomainUserById() {
        User expectedUser = user();
        UserPersistenceData data = mapper.toPersistence(expectedUser);
        when(repository.findById(USER_ID)).thenReturn(Optional.of(data));

        User actualUser = adapter.findById(USER_ID).orElseThrow();

        assertEquals(expectedUser.id(), actualUser.id());
        assertEquals(expectedUser.email(), actualUser.email());
        assertEquals(expectedUser.roles(), actualUser.roles());
    }

    @Test
    void delegatesCurrentEmailAndReservationChecksAsStrings() {
        when(repository.findByCurrentEmail(EMAIL.value())).thenReturn(Optional.empty());
        when(repository.findByAnyEmail(EMAIL.value())).thenReturn(Optional.empty());
        when(repository.existsByEmail(EMAIL.value())).thenReturn(true);

        assertEquals(Optional.empty(), adapter.findByEmail(EMAIL));
        assertEquals(Optional.empty(), adapter.findByCurrentOrPendingEmail(EMAIL));
        assertTrue(adapter.existsByEmail(EMAIL));
        verify(repository).findByCurrentEmail("anna@example.com");
        verify(repository).findByAnyEmail("anna@example.com");
        verify(repository).existsByEmail("anna@example.com");
    }

    @Test
    void mapsAndSavesDomainUser() {
        User user = user();

        adapter.save(user);

        ArgumentCaptor<UserPersistenceData> dataCaptor = ArgumentCaptor.forClass(UserPersistenceData.class);
        verify(repository).save(dataCaptor.capture());
        assertEquals(USER_ID, dataCaptor.getValue().user().getId());
        assertEquals("anna@example.com", dataCaptor.getValue().emails().getFirst().getEmail());
    }

    @Test
    void findsDomainUserForUpdate() {
        User expectedUser = user();
        UserPersistenceData data = mapper.toPersistence(expectedUser);
        when(repository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(data));

        User actualUser = adapter.findByIdForUpdate(USER_ID).orElseThrow();

        assertEquals(expectedUser.id(), actualUser.id());
        verify(repository).findByIdForUpdate(USER_ID);
    }

    @Test
    void translatesDuplicateKeyToEmailAlreadyExists() {
        User user = user();
        DuplicateKeyException duplicateKeyException = new DuplicateKeyException("duplicate email");
        doThrow(duplicateKeyException).when(repository).save(org.mockito.ArgumentMatchers.any());

        EmailAlreadyExistsException exception = assertThrows(
                EmailAlreadyExistsException.class,
                () -> adapter.save(user)
        );

        assertSame(duplicateKeyException, exception.getCause());
    }

    private static User user() {
        return User.reconstitute(
                USER_ID,
                EMAIL,
                null,
                new PasswordHash("stored-password-hash"),
                "Anna",
                "Petrova",
                LocalDate.of(2000, 1, 1),
                Set.of(UserRole.STUDENT),
                UserStatus.ACTIVE,
                CREATED_AT,
                CREATED_AT,
                CREATED_AT
        );
    }
}
