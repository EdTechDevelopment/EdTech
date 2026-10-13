package io.github.edtechdevelopment.tutoring.application.idempotency.port.in;

import io.github.edtechdevelopment.tutoring.application.idempotency.model.OperationType;
import io.github.edtechdevelopment.tutoring.application.idempotency.model.PayloadFingerprint;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CommandIdempotency {

    <T> Optional<T> beginOrReplay(
            UUID operationId,
            UUID userId,
            OperationType operationType,
            PayloadFingerprint payloadFingerprint,
            Class<T> resultType);

    void complete(
            UUID operationId,
            UUID userId,
            OperationType operationType,
            PayloadFingerprint payloadFingerprint,
            Object result,
            Instant completedAt);
}
