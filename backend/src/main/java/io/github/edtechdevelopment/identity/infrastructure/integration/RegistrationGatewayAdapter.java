package io.github.edtechdevelopment.identity.infrastructure.integration;

import io.github.edtechdevelopment.identity.api.IdentityRegistrationGateway;
import io.github.edtechdevelopment.identity.api.command.registration.RegistrationData;
import io.github.edtechdevelopment.identity.api.command.registration.RegistrationReceipt;
import io.github.edtechdevelopment.identity.application.command.account.RegisterUserCommand;
import io.github.edtechdevelopment.identity.application.command.account.RegistrationRole;
import io.github.edtechdevelopment.identity.application.port.in.account.RegisterUserUseCase;
import io.github.edtechdevelopment.identity.application.result.RegistrationResult;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class RegistrationGatewayAdapter implements IdentityRegistrationGateway {

    private final RegisterUserUseCase registerUserUseCase;

    public RegistrationGatewayAdapter(RegisterUserUseCase registerUserUseCase) {
        this.registerUserUseCase = Objects.requireNonNull(registerUserUseCase, "Register user use case must not be null");
    }

    @Override
    public RegistrationReceipt register(RegistrationData data) {
        Objects.requireNonNull(data, "Registration data must not be null");

        Set<RegistrationRole> roles = data.roles() == null ? null : data.roles().stream()
                .map(role -> RegistrationRole.valueOf(role.name()))
                .collect(Collectors.toUnmodifiableSet());
        RegistrationResult result = registerUserUseCase.register(new RegisterUserCommand(
                data.email(), data.rawPassword(), data.firstName(), data.lastName(), data.birthDate(), roles
        ));
        return new RegistrationReceipt(result.userId(), result.email(), result.verificationExpiresAt());
    }
}
