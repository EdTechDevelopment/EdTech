package io.github.edtechdevelopment.tutoring.application.idempotency.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CommandOperation(
        UUID operationId,
        UUID userId,
        OperationType operationType,
        PayloadFingerprint payloadFingerprint,
        String resultType,
        Object result,
        Instant completedAt
) {

    public CommandOperation {
        Objects.requireNonNull(operationId, "Operation id must not be null");
        Objects.requireNonNull(userId, "User id must not be null");
        Objects.requireNonNull(operationType, "Operation type must not be null");
        Objects.requireNonNull(payloadFingerprint, "Payload fingerprint must not be null");
        Objects.requireNonNull(resultType, "Result type must not be null");
        if (resultType.isBlank()) {
            throw new IllegalArgumentException("Result type must not be blank");
        }
        if ((result == null) != (completedAt == null)) {
            throw new IllegalArgumentException("Result and completion time must either both be present or both be absent");
        }
    }

    public static CommandOperation start(
            UUID operationId, UUID userId, OperationType operationType,
            PayloadFingerprint payloadFingerprint, Class<?> resultType) {

        Objects.requireNonNull(resultType, "Result type must not be null");
        return new CommandOperation(operationId, userId, operationType, payloadFingerprint,
                resultType.getName(), null, null);
    }

    public CommandOperation complete(Object result, Instant completedAt) {
        Objects.requireNonNull(result, "Operation result must not be null");
        Objects.requireNonNull(completedAt, "Completion time must not be null");
        if (this.result != null) {
            throw new IllegalStateException("Operation is already completed");
        }
        if (!result.getClass().getName().equals(resultType)) {
            throw new IllegalArgumentException("Result type does not match the reserved operation");
        }
        return new CommandOperation(operationId, userId, operationType, payloadFingerprint,
                resultType, result, completedAt);
    }
}
