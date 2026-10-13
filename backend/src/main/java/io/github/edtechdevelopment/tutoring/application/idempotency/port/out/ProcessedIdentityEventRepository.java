package io.github.edtechdevelopment.tutoring.application.idempotency.port.out;

import io.github.edtechdevelopment.tutoring.application.idempotency.model.ProcessedIdentityEvent;

import java.util.Optional;
import java.util.UUID;

public interface ProcessedIdentityEventRepository {

    Optional<ProcessedIdentityEvent> findByEventIdForUpdate(UUID eventId);

    void insert(ProcessedIdentityEvent event);
}
