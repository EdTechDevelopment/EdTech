package io.github.edtechdevelopment.identity.application.port.in.verification;

import io.github.edtechdevelopment.identity.application.command.verification.ResendEmailVerificationCommand;
import io.github.edtechdevelopment.identity.application.result.ResendVerificationResult;

public interface ResendEmailVerificationUseCase {

    ResendVerificationResult resendEmailVerification(ResendEmailVerificationCommand command);
}
