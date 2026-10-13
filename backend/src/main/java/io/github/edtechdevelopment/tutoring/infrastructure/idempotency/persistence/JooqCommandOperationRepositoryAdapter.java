package io.github.edtechdevelopment.tutoring.infrastructure.idempotency.persistence;

import io.github.edtechdevelopment.tutoring.application.idempotency.model.CommandOperation;
import io.github.edtechdevelopment.tutoring.application.idempotency.model.OperationType;
import io.github.edtechdevelopment.tutoring.application.idempotency.model.PayloadFingerprint;
import io.github.edtechdevelopment.tutoring.application.idempotency.port.out.CommandOperationRepository;
import io.github.edtechdevelopment.tutoring.infrastructure.persistence.PersistenceChecks;
import io.github.edtechdevelopment.tutoring.infrastructure.persistence.TransactionKeyLock;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static io.github.edtechdevelopment.tutoring.infrastructure.persistence.data.generated.Tables.TUTORING_COMMAND_OPERATIONS;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JooqCommandOperationRepositoryAdapter implements CommandOperationRepository {
    private final DSLContext dsl;
    private final TransactionKeyLock lock;
    private final CommandResultJson json;
    private final Clock clock;
    public JooqCommandOperationRepositoryAdapter(DSLContext dsl, TransactionKeyLock lock, CommandResultJson json, Clock clock) {
        this.dsl = dsl;
        this.lock = lock;
        this.json = json;
        this.clock = clock;
    }
    @Override
    public Optional<CommandOperation> findByOperationIdForUpdate(UUID id) {
        lock.lock("command", id.toString());
        return dsl.selectFrom(TUTORING_COMMAND_OPERATIONS).where(TUTORING_COMMAND_OPERATIONS.OPERATION_ID.eq(id))
                .forUpdate().fetchOptional().map(row -> {
                    OperationType type = OperationType.valueOf(row.getOperationType());
                    Object result = row.getResultPayload() == null ? null :
                            json.decode(type, row.getResultType(), row.getResultSchemaVersion(), row.getResultPayload().data());
                    return new CommandOperation(row.getOperationId(), row.getUserId(), type,
                            new PayloadFingerprint(row.getPayloadFingerprint()), row.getResultType(), result,
                            row.getCompletedAt() == null ? null : row.getCompletedAt().toInstant());
                });
    }
    @Override
    public void insert(CommandOperation operation) {
        dsl.insertInto(TUTORING_COMMAND_OPERATIONS)
                .set(TUTORING_COMMAND_OPERATIONS.OPERATION_ID, operation.operationId())
                .set(TUTORING_COMMAND_OPERATIONS.USER_ID, operation.userId())
                .set(TUTORING_COMMAND_OPERATIONS.OPERATION_TYPE, operation.operationType().name())
                .set(TUTORING_COMMAND_OPERATIONS.PAYLOAD_FINGERPRINT, operation.payloadFingerprint().value())
                .set(TUTORING_COMMAND_OPERATIONS.RESULT_TYPE, operation.resultType())
                .set(TUTORING_COMMAND_OPERATIONS.RESULT_SCHEMA_VERSION, 1)
                .set(TUTORING_COMMAND_OPERATIONS.CREATED_AT, clock.instant().atOffset(ZoneOffset.UTC)).execute();
    }
    @Override
    public void update(CommandOperation operation) {
        PersistenceChecks.requireOneRow(dsl.update(TUTORING_COMMAND_OPERATIONS)
                .set(TUTORING_COMMAND_OPERATIONS.RESULT_PAYLOAD, JSONB.valueOf(json.encode(operation.operationType(), operation.result())))
                .set(TUTORING_COMMAND_OPERATIONS.COMPLETED_AT, operation.completedAt().atOffset(ZoneOffset.UTC))
                .where(TUTORING_COMMAND_OPERATIONS.OPERATION_ID.eq(operation.operationId()))
                .and(TUTORING_COMMAND_OPERATIONS.USER_ID.eq(operation.userId()))
                .and(TUTORING_COMMAND_OPERATIONS.OPERATION_TYPE.eq(operation.operationType().name()))
                .and(TUTORING_COMMAND_OPERATIONS.PAYLOAD_FINGERPRINT.eq(operation.payloadFingerprint().value()))
                .and(TUTORING_COMMAND_OPERATIONS.COMPLETED_AT.isNull()).execute());
    }
}
