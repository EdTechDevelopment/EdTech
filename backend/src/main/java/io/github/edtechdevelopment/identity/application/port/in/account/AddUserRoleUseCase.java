package io.github.edtechdevelopment.identity.application.port.in.account;

import io.github.edtechdevelopment.identity.application.command.account.AssignRoleCommand;

public interface AddUserRoleUseCase {

    void addRole(AssignRoleCommand command);
}
