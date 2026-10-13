package io.github.edtechdevelopment.workflows.application.registration.model;

import java.time.Instant;
import java.util.Objects;

public record RegistrationOutcome(String email, Instant verificationExpiresAt) {

    public RegistrationOutcome {
        Objects.requireNonNull(email, "Email must not be null");
        Objects.requireNonNull(verificationExpiresAt, "Verification expiration must not be null");
    }

    @Override
    public String toString() {
        return "RegistrationOutcome[PROTECTED]";
    }
}

