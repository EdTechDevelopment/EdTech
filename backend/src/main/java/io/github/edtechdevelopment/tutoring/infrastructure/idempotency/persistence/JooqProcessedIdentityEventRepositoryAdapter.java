package io.github.edtechdevelopment.tutoring.infrastructure.idempotency.persistence;

import io.github.edtechdevelopment.tutoring.application.idempotency.model.PayloadFingerprint;
import io.github.edtechdevelopment.tutoring.application.idempotency.model.ProcessedIdentityEvent;
import io.github.edtechdevelopment.tutoring.application.idempotency.port.out.ProcessedIdentityEventRepository;
import io.github.edtechdevelopment.tutoring.infrastructure.persistence.TransactionKeyLock;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static io.github.edtechdevelopment.tutoring.infrastructure.persistence.data.generated.Tables.TUTORING_PROCESSED_IDENTITY_EVENTS;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JooqProcessedIdentityEventRepositoryAdapter implements ProcessedIdentityEventRepository {
    private final DSLContext dsl;
    private final TransactionKeyLock lock;
    public JooqProcessedIdentityEventRepositoryAdapter(DSLContext dsl, TransactionKeyLock lock) {
        this.dsl = dsl;
        this.lock = lock;
    }

    @Override
    public Optional<ProcessedIdentityEvent> findByEventIdForUpdate(UUID id) {
        lock.lock("event", id.toString());
        return dsl.selectFrom(TUTORING_PROCESSED_IDENTITY_EVENTS).where(TUTORING_PROCESSED_IDENTITY_EVENTS.EVENT_ID.eq(id))
                .forUpdate().fetchOptional().map(row -> new ProcessedIdentityEvent(row.getEventId(), row.getUserId(),
                        row.getEventType(), row.getFingerprintVersion(), new PayloadFingerprint(row.getPayloadFingerprint()),
                        row.getProcessedAt().toInstant()));
    }
    @Override
    public void insert(ProcessedIdentityEvent event) {
        dsl.insertInto(TUTORING_PROCESSED_IDENTITY_EVENTS)
                .set(TUTORING_PROCESSED_IDENTITY_EVENTS.EVENT_ID, event.eventId())
                .set(TUTORING_PROCESSED_IDENTITY_EVENTS.USER_ID, event.userId())
                .set(TUTORING_PROCESSED_IDENTITY_EVENTS.EVENT_TYPE, event.eventType())
                .set(TUTORING_PROCESSED_IDENTITY_EVENTS.FINGERPRINT_VERSION, event.fingerprintVersion())
                .set(TUTORING_PROCESSED_IDENTITY_EVENTS.PAYLOAD_FINGERPRINT, event.payloadFingerprint().value())
                .set(TUTORING_PROCESSED_IDENTITY_EVENTS.PROCESSED_AT, event.processedAt().atOffset(ZoneOffset.UTC)).execute();
    }
}
