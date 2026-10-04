package io.github.edtechdevelopment.identity.application.command.account;

import java.util.UUID;

public record AssignRoleCommand(UUID userId, RegistrationRole role) {
}
