package io.github.edtechdevelopment.identity.presentation.auth.controller;

import io.github.edtechdevelopment.identity.application.port.in.authentication.LoginUseCase;
import io.github.edtechdevelopment.identity.application.port.in.authentication.LogoutUseCase;
import io.github.edtechdevelopment.identity.application.port.in.authentication.RefreshTokenUseCase;
import io.github.edtechdevelopment.identity.application.result.AuthenticationResult;
import io.github.edtechdevelopment.identity.presentation.auth.cookie.RefreshTokenCookieFactory;
import io.github.edtechdevelopment.identity.presentation.auth.mapper.AuthPresentationMapper;
import io.github.edtechdevelopment.identity.presentation.auth.model.request.LoginRequest;
import io.github.edtechdevelopment.identity.presentation.auth.model.response.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/api/v1/auth")
public final class AuthController {

    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final AuthPresentationMapper mapper;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    public AuthController(
            LoginUseCase loginUseCase,
            RefreshTokenUseCase refreshTokenUseCase,
            LogoutUseCase logoutUseCase,
            AuthPresentationMapper mapper,
            RefreshTokenCookieFactory refreshTokenCookieFactory
    ) {
        this.loginUseCase = Objects.requireNonNull(loginUseCase, "Login use case must not be null");
        this.refreshTokenUseCase = Objects.requireNonNull(
                refreshTokenUseCase,
                "Refresh token use case must not be null"
        );
        this.logoutUseCase = Objects.requireNonNull(logoutUseCase, "Logout use case must not be null");
        this.mapper = Objects.requireNonNull(mapper, "Auth presentation mapper must not be null");
        this.refreshTokenCookieFactory = Objects.requireNonNull(
                refreshTokenCookieFactory,
                "Refresh token cookie factory must not be null"
        );
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthenticationResult result = loginUseCase.login(mapper.toCommand(request));
        ResponseCookie refreshCookie = refreshTokenCookieFactory.create(result.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(mapper.toResponse(result));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false)
            String rawRefreshToken
    ) {
        AuthenticationResult result = refreshTokenUseCase.refresh(
                mapper.toRefreshTokenCommand(rawRefreshToken)
        );
        ResponseCookie refreshCookie = refreshTokenCookieFactory.create(result.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(mapper.toResponse(result));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false)
            String rawRefreshToken
    ) {
        logoutUseCase.logout(mapper.toLogoutCommand(rawRefreshToken));

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.clear().toString())
                .build();
    }
}
