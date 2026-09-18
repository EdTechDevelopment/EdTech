package io.github.edtechdevelopment.identity.presentation.auth.controller;

import io.github.edtechdevelopment.identity.application.port.in.account.RegisterUserUseCase;
import io.github.edtechdevelopment.identity.application.result.RegistrationResult;
import io.github.edtechdevelopment.identity.presentation.auth.mapper.AuthPresentationMapper;
import io.github.edtechdevelopment.identity.presentation.auth.model.request.RegisterRequest;
import io.github.edtechdevelopment.identity.presentation.auth.model.response.VerificationPendingResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/api/v1/auth")
public final class AuthController {

    private final RegisterUserUseCase registerUserUseCase;
    private final AuthPresentationMapper mapper;

    public AuthController(RegisterUserUseCase registerUserUseCase, AuthPresentationMapper mapper) {
        this.registerUserUseCase = Objects.requireNonNull(
                registerUserUseCase,
                "Register user use case must not be null"
        );
        this.mapper = Objects.requireNonNull(mapper, "Auth presentation mapper must not be null");
    }

    @PostMapping("/register")
    public ResponseEntity<VerificationPendingResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegistrationResult result = registerUserUseCase.register(mapper.toCommand(request));
        return ResponseEntity.accepted().body(mapper.toResponse(result));
    }
}
