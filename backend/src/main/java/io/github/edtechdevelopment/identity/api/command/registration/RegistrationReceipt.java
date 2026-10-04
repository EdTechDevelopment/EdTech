package io.github.edtechdevelopment.identity.api.command.registration;

import java.time.Instant;
import java.util.UUID;

public record RegistrationReceipt(UUID userId, String email, Instant verificationExpiresAt) {
}
