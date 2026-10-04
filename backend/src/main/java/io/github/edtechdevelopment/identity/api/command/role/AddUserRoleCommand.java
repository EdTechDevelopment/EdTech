package io.github.edtechdevelopment.identity.api.command.role;

import io.github.edtechdevelopment.identity.api.model.UserRoleView;

import java.util.UUID;

public record AddUserRoleCommand(UUID userId, UserRoleView role) {
}
