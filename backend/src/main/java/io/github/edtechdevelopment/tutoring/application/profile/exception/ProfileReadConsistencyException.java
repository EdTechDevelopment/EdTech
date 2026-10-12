package io.github.edtechdevelopment.tutoring.application.profile.exception;

import java.util.Objects;
import java.util.UUID;

public final class ProfileReadConsistencyException extends RuntimeException {

    private final UUID userId;

    public ProfileReadConsistencyException(UUID userId, String reason) {
        super(reason);
        this.userId = Objects.requireNonNull(userId, "User id must not be null");
    }

    public UUID userId() {
        return userId;
    }
}
