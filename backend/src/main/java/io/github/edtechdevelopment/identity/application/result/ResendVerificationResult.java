package io.github.edtechdevelopment.identity.application.result;

import java.time.Instant;

public record ResendVerificationResult(
        String email,
        Instant verificationExpiresAt
) {
}
