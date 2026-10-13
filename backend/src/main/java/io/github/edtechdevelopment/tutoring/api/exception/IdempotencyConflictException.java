package io.github.edtechdevelopment.tutoring.api.exception;

import java.util.Objects;
import java.util.UUID;

public final class IdempotencyConflictException extends RuntimeException {

    private final UUID operationId;

    public IdempotencyConflictException(UUID operationId) {
        super("Operation id was already used with different data");
        this.operationId = Objects.requireNonNull(operationId, "Operation id must not be null");
    }

    public UUID operationId() {
        return operationId;
    }
}
