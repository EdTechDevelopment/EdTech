package io.github.edtechdevelopment.identity.application.command.account;

import java.time.LocalDate;
import java.util.Set;

public record RegisterUserCommand(
        String email,
        String rawPassword,
        String firstName,
        String lastName,
        LocalDate birthDate,
        Set<RegistrationRole> roles
) {

    public RegisterUserCommand {
        roles = roles == null ? null : Set.copyOf(roles);
    }

    @Override
    public String toString() {
        return "RegisterUserCommand[PROTECTED]";
    }
}
