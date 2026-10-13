package io.github.edtechdevelopment.workflows.presentation.registration.model;

import java.time.Instant;

public record VerificationPendingResponse(String email, Instant verificationExpiresAt) {
}

