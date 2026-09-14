package io.github.edtechdevelopment.identity.application.command.account;

import io.github.edtechdevelopment.identity.domain.user.model.UserRole;

import java.util.Set;

public record RegisterUserCommand(
        String email,
        String rawPassword,
        String firstName,
        String lastName,
        Set<UserRole> roles
) {

    public RegisterUserCommand {
        roles = roles == null ? null : Set.copyOf(roles);
    }

    @Override
    public String toString() {
        return "RegisterUserCommand[PROTECTED]";
    }
}
