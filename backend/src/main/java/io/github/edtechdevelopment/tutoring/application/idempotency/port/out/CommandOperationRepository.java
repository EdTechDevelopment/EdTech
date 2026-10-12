package io.github.edtechdevelopment.tutoring.application.idempotency.port.out;

import io.github.edtechdevelopment.tutoring.application.idempotency.model.CommandOperation;

import java.util.Optional;
import java.util.UUID;

public interface CommandOperationRepository {

    Optional<CommandOperation> findByOperationIdForUpdate(UUID operationId);

    void insert(CommandOperation operation);

    void update(CommandOperation operation);
}
