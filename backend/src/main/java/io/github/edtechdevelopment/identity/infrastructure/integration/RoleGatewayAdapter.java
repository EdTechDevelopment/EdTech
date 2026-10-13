package io.github.edtechdevelopment.identity.infrastructure.integration;

import io.github.edtechdevelopment.identity.api.IdentityRoleGateway;
import io.github.edtechdevelopment.identity.api.exception.IdentityCommandException;
import io.github.edtechdevelopment.identity.application.exception.InvalidUseCaseInputException;
import io.github.edtechdevelopment.identity.domain.user.exception.InvalidUserStateException;
import io.github.edtechdevelopment.identity.api.command.role.AddUserRoleCommand;
import io.github.edtechdevelopment.identity.application.command.account.AssignRoleCommand;
import io.github.edtechdevelopment.identity.application.command.account.RegistrationRole;
import io.github.edtechdevelopment.identity.application.port.in.account.AddUserRoleUseCase;

import java.util.Objects;

public final class RoleGatewayAdapter implements IdentityRoleGateway {

    private final AddUserRoleUseCase addUserRoleUseCase;

    public RoleGatewayAdapter(AddUserRoleUseCase addUserRoleUseCase) {
        this.addUserRoleUseCase = Objects.requireNonNull(addUserRoleUseCase, "Add user role use case must not be null");
    }

    @Override
    public void addRole(AddUserRoleCommand command) {
        Objects.requireNonNull(command, "Add user role command must not be null");
        RegistrationRole role = command.role() == null ? null : RegistrationRole.valueOf(command.role().name());
        try {
            addUserRoleUseCase.addRole(new AssignRoleCommand(command.userId(), role));
        } catch (InvalidUserStateException exception) {
            throw new IdentityCommandException(IdentityCommandException.Reason.ACCOUNT_STATE_CONFLICT);
        } catch (InvalidUseCaseInputException exception) {
            throw new IdentityCommandException(IdentityCommandException.Reason.INVALID_DATA);
        }
    }
}
