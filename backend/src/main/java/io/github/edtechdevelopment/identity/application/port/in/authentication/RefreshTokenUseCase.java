package io.github.edtechdevelopment.identity.application.port.in.authentication;

import io.github.edtechdevelopment.identity.application.command.authentication.RefreshTokenCommand;
import io.github.edtechdevelopment.identity.application.result.AuthenticationResult;

public interface RefreshTokenUseCase {

    AuthenticationResult refresh(RefreshTokenCommand command);
}
