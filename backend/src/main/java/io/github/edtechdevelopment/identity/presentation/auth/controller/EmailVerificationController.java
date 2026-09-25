package io.github.edtechdevelopment.identity.presentation.auth.controller;

import io.github.edtechdevelopment.identity.application.port.in.verification.ConfirmEmailUseCase;
import io.github.edtechdevelopment.identity.application.port.in.verification.ResendEmailVerificationUseCase;
import io.github.edtechdevelopment.identity.application.result.AuthenticationResult;
import io.github.edtechdevelopment.identity.presentation.auth.cookie.RefreshTokenCookieFactory;
import io.github.edtechdevelopment.identity.presentation.auth.mapper.AuthPresentationMapper;
import io.github.edtechdevelopment.identity.presentation.auth.model.request.ConfirmEmailRequest;
import io.github.edtechdevelopment.identity.presentation.auth.model.request.ResendEmailVerificationRequest;
import io.github.edtechdevelopment.identity.presentation.auth.model.response.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/api/v1/auth/email-verification")
public final class EmailVerificationController {

    private final ConfirmEmailUseCase confirmEmailUseCase;
    private final ResendEmailVerificationUseCase resendEmailVerificationUseCase;
    private final AuthPresentationMapper mapper;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    public EmailVerificationController(
            ConfirmEmailUseCase confirmEmailUseCase,
            ResendEmailVerificationUseCase resendEmailVerificationUseCase,
            AuthPresentationMapper mapper,
            RefreshTokenCookieFactory refreshTokenCookieFactory
    ) {
        this.confirmEmailUseCase = Objects.requireNonNull(
                confirmEmailUseCase,
                "Confirm email use case must not be null"
        );
        this.resendEmailVerificationUseCase = Objects.requireNonNull(
                resendEmailVerificationUseCase,
                "Resend email verification use case must not be null"
        );
        this.mapper = Objects.requireNonNull(mapper, "Auth presentation mapper must not be null");
        this.refreshTokenCookieFactory = Objects.requireNonNull(
                refreshTokenCookieFactory,
                "Refresh token cookie factory must not be null"
        );
    }

    @PostMapping("/confirm")
    public ResponseEntity<TokenResponse> confirmEmail(@Valid @RequestBody ConfirmEmailRequest request) {
        AuthenticationResult result = confirmEmailUseCase.confirmEmail(mapper.toCommand(request));
        ResponseCookie refreshCookie = refreshTokenCookieFactory.create(result.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(mapper.toResponse(result));
    }

    @PostMapping("/resend")
    public ResponseEntity<Void> resendEmailVerification(
            @Valid @RequestBody ResendEmailVerificationRequest request
    ) {
        resendEmailVerificationUseCase.resendEmailVerification(mapper.toCommand(request));
        return ResponseEntity.accepted().build();
    }
}
