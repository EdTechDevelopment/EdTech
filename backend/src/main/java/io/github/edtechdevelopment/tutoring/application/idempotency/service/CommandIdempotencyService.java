package io.github.edtechdevelopment.tutoring.application.idempotency.service;

import io.github.edtechdevelopment.tutoring.api.exception.IdempotencyConflictException;
import io.github.edtechdevelopment.tutoring.application.idempotency.model.CommandOperation;
import io.github.edtechdevelopment.tutoring.application.idempotency.model.OperationType;
import io.github.edtechdevelopment.tutoring.application.idempotency.model.PayloadFingerprint;
import io.github.edtechdevelopment.tutoring.application.idempotency.port.in.CommandIdempotency;
import io.github.edtechdevelopment.tutoring.application.idempotency.port.out.CommandOperationRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class CommandIdempotencyService implements CommandIdempotency {

    private final CommandOperationRepository repository;

    public CommandIdempotencyService(CommandOperationRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Command operation repository must not be null");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> Optional<T> beginOrReplay(
            UUID operationId, UUID userId, OperationType operationType,
            PayloadFingerprint payloadFingerprint, Class<T> resultType) {

        Objects.requireNonNull(operationId, "Operation id must not be null");
        Objects.requireNonNull(userId, "User id must not be null");
        Objects.requireNonNull(operationType, "Operation type must not be null");
        Objects.requireNonNull(payloadFingerprint, "Payload fingerprint must not be null");
        Objects.requireNonNull(resultType, "Result type must not be null");

        Optional<CommandOperation> existing = repository.findByOperationIdForUpdate(operationId);
        if (existing.isEmpty()) {
            repository.insert(CommandOperation.start(operationId, userId, operationType, payloadFingerprint, resultType));
            return Optional.empty();
        }

        CommandOperation operation = existing.get();
        requireSameCommand(operation, operationId, userId, operationType, payloadFingerprint);
        if (!operation.resultType().equals(resultType.getName())) {
            throw new IdempotencyConflictException(operationId);
        }
        if (operation.completedAt() == null) {
            throw new IllegalStateException("A committed idempotency operation has no result");
        }
        if (!resultType.isInstance(operation.result())) {
            throw new IllegalStateException("Stored idempotency result has an unexpected type");
        }
        return Optional.of(resultType.cast(operation.result()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(
            UUID operationId, UUID userId, OperationType operationType,
            PayloadFingerprint payloadFingerprint, Object result, Instant completedAt) {

        Objects.requireNonNull(operationId, "Operation id must not be null");
        Objects.requireNonNull(userId, "User id must not be null");
        Objects.requireNonNull(operationType, "Operation type must not be null");
        Objects.requireNonNull(payloadFingerprint, "Payload fingerprint must not be null");
        Objects.requireNonNull(result, "Operation result must not be null");
        Objects.requireNonNull(completedAt, "Completion time must not be null");

        CommandOperation operation = repository.findByOperationIdForUpdate(operationId)
                .orElseThrow(() -> new IllegalStateException("Idempotency operation was not started"));
        requireSameCommand(operation, operationId, userId, operationType, payloadFingerprint);
        repository.update(operation.complete(result, completedAt));
    }

    private static void requireSameCommand(
            CommandOperation operation, UUID operationId, UUID userId,
            OperationType operationType, PayloadFingerprint payloadFingerprint) {

        if (!operation.userId().equals(userId)
                || operation.operationType() != operationType
                || !operation.payloadFingerprint().equals(payloadFingerprint)) {
            throw new IdempotencyConflictException(operationId);
        }
    }
}
