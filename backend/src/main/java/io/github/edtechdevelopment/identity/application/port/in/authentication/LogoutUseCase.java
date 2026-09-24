package io.github.edtechdevelopment.identity.application.port.in.authentication;

import io.github.edtechdevelopment.identity.application.command.authentication.LogoutCommand;

public interface LogoutUseCase {

    void logout(LogoutCommand command);
}
