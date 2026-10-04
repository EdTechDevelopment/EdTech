package io.github.edtechdevelopment.identity.presentation.auth.mapper;

import io.github.edtechdevelopment.identity.application.command.account.RegisterUserCommand;
import io.github.edtechdevelopment.identity.application.command.authentication.LoginCommand;
import io.github.edtechdevelopment.identity.application.command.authentication.LogoutCommand;
import io.github.edtechdevelopment.identity.application.command.authentication.RefreshTokenCommand;
import io.github.edtechdevelopment.identity.application.command.verification.ConfirmEmailCommand;
import io.github.edtechdevelopment.identity.application.command.verification.ResendEmailVerificationCommand;
import io.github.edtechdevelopment.identity.application.result.AuthenticationResult;
import io.github.edtechdevelopment.identity.application.result.RegistrationResult;
import io.github.edtechdevelopment.identity.presentation.auth.model.request.ConfirmEmailRequest;
import io.github.edtechdevelopment.identity.presentation.auth.model.request.LoginRequest;
import io.github.edtechdevelopment.identity.presentation.auth.model.request.RegisterRequest;
import io.github.edtechdevelopment.identity.presentation.auth.model.request.ResendEmailVerificationRequest;
import io.github.edtechdevelopment.identity.presentation.auth.model.response.TokenResponse;
import io.github.edtechdevelopment.identity.presentation.auth.model.response.VerificationPendingResponse;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

@Component
public final class AuthPresentationMapper {

    private static final String BEARER_TOKEN_TYPE = "Bearer";

    private final Clock clock;

    public AuthPresentationMapper(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
    }

    public RegisterUserCommand toCommand(RegisterRequest request) {
        Objects.requireNonNull(request, "Register request must not be null");
        return new RegisterUserCommand(
                request.email(),
                request.password(),
                request.firstName(),
                request.lastName(),
                request.birthDate(),
                Set.copyOf(request.roles())
        );
    }

    public VerificationPendingResponse toResponse(RegistrationResult result) {
        Objects.requireNonNull(result, "Registration result must not be null");
        return new VerificationPendingResponse(result.email(), result.verificationExpiresAt());
    }

    public LoginCommand toCommand(LoginRequest request) {
        Objects.requireNonNull(request, "Login request must not be null");
        return new LoginCommand(request.email(), request.password());
    }

    public RefreshTokenCommand toRefreshTokenCommand(String rawRefreshToken) {
        return new RefreshTokenCommand(rawRefreshToken);
    }

    public LogoutCommand toLogoutCommand(String rawRefreshToken) {
        return new LogoutCommand(rawRefreshToken);
    }

    public ConfirmEmailCommand toCommand(ConfirmEmailRequest request) {
        Objects.requireNonNull(request, "Confirm email request must not be null");
        return new ConfirmEmailCommand(request.token());
    }

    public ResendEmailVerificationCommand toCommand(ResendEmailVerificationRequest request) {
        Objects.requireNonNull(request, "Resend email verification request must not be null");
        return new ResendEmailVerificationCommand(request.email());
    }

    public TokenResponse toResponse(AuthenticationResult result) {
        Objects.requireNonNull(result, "Authentication result must not be null");
        return new TokenResponse(
                result.accessToken().value(),
                BEARER_TOKEN_TYPE,
                remainingLifetimeSeconds(result.accessToken().expiresAt())
        );
    }

    private long remainingLifetimeSeconds(Instant expiresAt) {
        Duration remaining = Duration.between(clock.instant(), expiresAt);
        if (remaining.isZero() || remaining.isNegative()) {
            throw new IllegalArgumentException("Access token must not be expired when response is created");
        }

        long seconds = remaining.getSeconds();
        return remaining.getNano() > 0 ? seconds + 1 : seconds;
    }
}
