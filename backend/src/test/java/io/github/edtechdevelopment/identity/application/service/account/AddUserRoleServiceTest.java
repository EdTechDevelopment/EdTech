package io.github.edtechdevelopment.identity.application.service.account;

import io.github.edtechdevelopment.identity.application.command.account.AssignRoleCommand;
import io.github.edtechdevelopment.identity.application.command.account.RegistrationRole;
import io.github.edtechdevelopment.identity.application.port.out.TimeProvider;
import io.github.edtechdevelopment.identity.application.port.out.persistence.UserRepository;
import io.github.edtechdevelopment.identity.domain.user.exception.InvalidUserStateException;
import io.github.edtechdevelopment.identity.domain.user.model.Email;
import io.github.edtechdevelopment.identity.domain.user.model.PasswordHash;
import io.github.edtechdevelopment.identity.domain.user.model.User;
import io.github.edtechdevelopment.identity.domain.user.model.UserRole;
import io.github.edtechdevelopment.identity.domain.user.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AddUserRoleServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant CREATED_AT = Instant.parse("2026-09-01T10:00:00Z");
    private static final Instant CHANGED_AT = CREATED_AT.plusSeconds(60);

    private UserRepository userRepository;
    private TimeProvider timeProvider;
    private AddUserRoleService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        timeProvider = mock(TimeProvider.class);
        service = new AddUserRoleService(userRepository, timeProvider);
    }

    @Test
    void addsRoleToActiveUser() {
        User user = user(UserStatus.ACTIVE);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(timeProvider.now()).thenReturn(CHANGED_AT);

        service.addRole(command());

        assertEquals(Set.of(UserRole.STUDENT, UserRole.TEACHER), user.roles());
        verify(userRepository).save(user);
    }

    @Test
    void rejectsRoleAlreadyAssignedToUser() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user(UserStatus.ACTIVE)));
        when(timeProvider.now()).thenReturn(CHANGED_AT);

        assertThrows(InvalidUserStateException.class, () -> service.addRole(
                new AssignRoleCommand(USER_ID, RegistrationRole.STUDENT)
        ));
    }

    @Test
    void rejectsRoleForInactiveUser() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user(UserStatus.SUSPENDED)));
        when(timeProvider.now()).thenReturn(CHANGED_AT);

        assertThrows(InvalidUserStateException.class, () -> service.addRole(command()));
    }

    private static AssignRoleCommand command() {
        return new AssignRoleCommand(USER_ID, RegistrationRole.TEACHER);
    }

    private static User user(UserStatus status) {
        return User.reconstitute(
                USER_ID, new Email("anna@example.com"), null, new PasswordHash("stored-password-hash"),
                "Anna", "Petrova", LocalDate.of(2000, 1, 1), Set.of(UserRole.STUDENT), status,
                CREATED_AT, CREATED_AT, CREATED_AT
        );
    }
}
