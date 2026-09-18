package io.github.edtechdevelopment.identity.presentation.auth.mapper;

import io.github.edtechdevelopment.identity.application.command.account.RegisterUserCommand;
import io.github.edtechdevelopment.identity.application.result.RegistrationResult;
import io.github.edtechdevelopment.identity.presentation.auth.model.request.RegisterRequest;
import io.github.edtechdevelopment.identity.presentation.auth.model.response.VerificationPendingResponse;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public final class AuthPresentationMapper {

    public RegisterUserCommand toCommand(RegisterRequest request) {
        Objects.requireNonNull(request, "Register request must not be null");
        return new RegisterUserCommand(
                request.email(),
                request.password(),
                request.firstName(),
                request.lastName(),
                request.roles()
        );
    }

    public VerificationPendingResponse toResponse(RegistrationResult result) {
        Objects.requireNonNull(result, "Registration result must not be null");
        return new VerificationPendingResponse(result.email(), result.verificationExpiresAt());
    }
}
