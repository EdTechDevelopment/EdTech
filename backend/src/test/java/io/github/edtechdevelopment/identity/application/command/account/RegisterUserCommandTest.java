package io.github.edtechdevelopment.identity.application.command.account;

import io.github.edtechdevelopment.identity.domain.user.model.UserRole;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegisterUserCommandTest {

    @Test
    void storesRegistrationData() {
        RegisterUserCommand command = registrationCommand(Set.of(UserRole.STUDENT));

        assertEquals("anna@example.com", command.email());
        assertEquals("secret-password", command.rawPassword());
        assertEquals("Anna", command.firstName());
        assertEquals("Petrova", command.lastName());
        assertEquals(Set.of(UserRole.STUDENT), command.roles());
    }

    @Test
    void protectsRolesFromExternalModification() {
        Set<UserRole> sourceRoles = EnumSet.of(UserRole.STUDENT);
        RegisterUserCommand command = registrationCommand(sourceRoles);

        sourceRoles.add(UserRole.TEACHER);

        assertEquals(Set.of(UserRole.STUDENT), command.roles());
        assertThrows(
                UnsupportedOperationException.class,
                () -> command.roles().add(UserRole.TEACHER));
    }

    @Test
    void doesNotExposeRegistrationDataInToString() {
        RegisterUserCommand command = registrationCommand(Set.of(UserRole.STUDENT));

        String printedCommand = command.toString();

        assertEquals("RegisterUserCommand[PROTECTED]", printedCommand);
        assertFalse(printedCommand.contains(command.email()));
        assertFalse(printedCommand.contains(command.rawPassword()));
        assertFalse(printedCommand.contains(command.firstName()));
        assertFalse(printedCommand.contains(command.lastName()));
    }

    private static RegisterUserCommand registrationCommand(Set<UserRole> roles) {
        return new RegisterUserCommand(
                "anna@example.com",
                "secret-password",
                "Anna",
                "Petrova",
                roles);
    }
}
