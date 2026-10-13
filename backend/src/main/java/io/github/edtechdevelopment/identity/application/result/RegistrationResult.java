package io.github.edtechdevelopment.identity.application.result;

import java.time.Instant;
import java.util.UUID;

public record RegistrationResult(UUID userId, String email, Instant verificationExpiresAt) {
}
