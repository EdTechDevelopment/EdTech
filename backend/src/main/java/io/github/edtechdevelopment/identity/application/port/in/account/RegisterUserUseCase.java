package io.github.edtechdevelopment.identity.application.port.in.account;

import io.github.edtechdevelopment.identity.application.command.account.RegisterUserCommand;
import io.github.edtechdevelopment.identity.application.result.RegistrationResult;

public interface RegisterUserUseCase {

    RegistrationResult register(RegisterUserCommand command);
}
