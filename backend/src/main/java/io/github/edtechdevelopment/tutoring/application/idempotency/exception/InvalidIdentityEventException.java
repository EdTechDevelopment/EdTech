package io.github.edtechdevelopment.tutoring.application.idempotency.exception;

import java.util.UUID;

public final class InvalidIdentityEventException extends RuntimeException {

    public InvalidIdentityEventException(UUID eventId) {
        super("Identity event id was already used with different data");
    }
}
