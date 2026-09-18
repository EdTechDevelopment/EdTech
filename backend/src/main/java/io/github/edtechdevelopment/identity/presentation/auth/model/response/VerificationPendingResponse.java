package io.github.edtechdevelopment.identity.presentation.auth.model.response;

import java.time.Instant;

public record VerificationPendingResponse(
        String email,
        Instant verificationExpiresAt
) {
}
