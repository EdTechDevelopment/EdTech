package io.github.edtechdevelopment.identity.application.port.in.verification;

import io.github.edtechdevelopment.identity.application.command.verification.ConfirmEmailCommand;
import io.github.edtechdevelopment.identity.application.result.AuthenticationResult;

public interface ConfirmEmailUseCase {

    AuthenticationResult confirmEmail(ConfirmEmailCommand command);
}
