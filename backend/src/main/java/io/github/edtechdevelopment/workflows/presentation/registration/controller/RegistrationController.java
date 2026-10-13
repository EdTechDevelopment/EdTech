package io.github.edtechdevelopment.workflows.presentation.registration.controller;

import io.github.edtechdevelopment.workflows.application.registration.port.in.RegisterAccountUseCase;
import io.github.edtechdevelopment.workflows.presentation.registration.mapper.RegistrationPresentationMapper;
import io.github.edtechdevelopment.workflows.presentation.registration.model.RegistrationRequest;
import io.github.edtechdevelopment.workflows.presentation.registration.model.VerificationPendingResponse;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class RegistrationController {

    private final RegisterAccountUseCase registration;
    private final RegistrationPresentationMapper mapper;

    public RegistrationController(RegisterAccountUseCase registration, RegistrationPresentationMapper mapper) {
        this.registration = registration;
        this.mapper = mapper;
    }

    @PostMapping("/api/v1/auth/register")
    public ResponseEntity<VerificationPendingResponse> register(
            @RequestHeader("Idempotency-Key") UUID operationId,
            @Valid @RequestBody RegistrationRequest request) {
        return ResponseEntity.accepted().body(mapper.toResponse(registration.register(mapper.toCommand(operationId, request))));
    }
}

