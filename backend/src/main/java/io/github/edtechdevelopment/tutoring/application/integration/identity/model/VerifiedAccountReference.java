package io.github.edtechdevelopment.tutoring.application.integration.identity.model;

import java.util.Objects;
import java.util.UUID;

public record VerifiedAccountReference(UUID userId, String email) {

    public VerifiedAccountReference {
        Objects.requireNonNull(userId, "User id must not be null");
        Objects.requireNonNull(email, "Verified email must not be null");
        if (email.isBlank()) {
            throw new IllegalArgumentException("Verified email must not be blank");
        }
    }
}
