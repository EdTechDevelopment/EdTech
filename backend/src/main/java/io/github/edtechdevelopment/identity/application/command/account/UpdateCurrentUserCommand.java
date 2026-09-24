package io.github.edtechdevelopment.identity.application.command.account;

import java.util.UUID;

public record UpdateCurrentUserCommand(
        UUID userId,
        String email,
        String firstName,
        String lastName
) {

    @Override
    public String toString() {
        return "UpdateCurrentUserCommand[PROTECTED]";
    }
}
