package io.github.edtechdevelopment.identity.application.port.in.authentication;

import io.github.edtechdevelopment.identity.application.command.authentication.LoginCommand;
import io.github.edtechdevelopment.identity.application.result.AuthenticationResult;

public interface LoginUseCase {

    AuthenticationResult login(LoginCommand command);
}
