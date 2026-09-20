package io.github.edtechdevelopment.identity.application.result;

import java.time.Instant;

public record RegistrationResult(
        String email,
        Instant verificationExpiresAt
) {
}
