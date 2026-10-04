package io.github.edtechdevelopment.identity.api;

import io.github.edtechdevelopment.identity.api.command.role.AddUserRoleCommand;

public interface IdentityRoleGateway {

    void addRole(AddUserRoleCommand command);
}
