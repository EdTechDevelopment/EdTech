package io.github.edtechdevelopment.tutoring.application.idempotency.service;

import io.github.edtechdevelopment.tutoring.application.idempotency.exception.InvalidIdentityEventException;
import io.github.edtechdevelopment.tutoring.application.idempotency.model.PayloadFingerprint;
import io.github.edtechdevelopment.tutoring.application.idempotency.model.ProcessedIdentityEvent;
import io.github.edtechdevelopment.tutoring.application.idempotency.port.out.ProcessedIdentityEventRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class IdentityEventDeduplicationService {

    private final ProcessedIdentityEventRepository repository;

    public IdentityEventDeduplicationService(ProcessedIdentityEventRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Processed event repository must not be null");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean beginIfNotProcessed(
            UUID eventId, UUID userId, String eventType, int fingerprintVersion,
            PayloadFingerprint payloadFingerprint, Instant processedAt) {

        ProcessedIdentityEvent candidate = new ProcessedIdentityEvent(
                eventId, userId, eventType, fingerprintVersion, payloadFingerprint, processedAt);
        var existing = repository.findByEventIdForUpdate(candidate.eventId());
        if (existing.isPresent()) {
            ProcessedIdentityEvent event = existing.get();
            if (!event.userId().equals(candidate.userId())
                    || !event.eventType().equals(candidate.eventType())
                    || event.fingerprintVersion() != candidate.fingerprintVersion()
                    || !event.payloadFingerprint().equals(candidate.payloadFingerprint())) {
                throw new InvalidIdentityEventException(candidate.eventId());
            }
            return false;
        }

        repository.insert(candidate);
        return true;
    }
}
