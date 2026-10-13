package io.github.edtechdevelopment.workflows.infrastructure.persistence;

import io.github.edtechdevelopment.workflows.application.exception.WorkflowException;
import io.github.edtechdevelopment.workflows.application.idempotency.model.WorkflowOperationType;
import io.github.edtechdevelopment.workflows.application.idempotency.port.out.WorkflowOperationRepository;
import io.github.edtechdevelopment.workflows.application.me.model.MeResult;
import io.github.edtechdevelopment.workflows.application.registration.model.RegistrationOutcome;
import io.github.edtechdevelopment.workflows.infrastructure.persistence.data.generated.tables.records.WorkflowsOperationsRecord;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static io.github.edtechdevelopment.workflows.infrastructure.persistence.data.generated.Tables.WORKFLOWS_OPERATIONS;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JooqWorkflowOperationRepository implements WorkflowOperationRepository {

    private final DSLContext dsl;
    private final ObjectMapper json;

    public JooqWorkflowOperationRepository(DSLContext dsl, ObjectMapper json) {
        this.dsl = dsl;
        this.json = json;
    }

    @Override
    public Optional<RegistrationOutcome> beginRegistration(UUID operationId, String email, String fingerprint, Instant now) {
        return begin(operationId, email, WorkflowOperationType.REGISTER, fingerprint, now)
                .map(row -> json.readValue(row.getResultPayload().data(), RegistrationOutcome.class));
    }

    @Override
    public void completeRegistration(UUID operationId, String email, String fingerprint, RegistrationOutcome result, Instant now) {
        if (!result.email().equals(email)) {
            throw new IllegalStateException("Registration result does not match its operation context");
        }
        complete(operationId, email, WorkflowOperationType.REGISTER, fingerprint, json.writeValueAsString(result), now);
    }

    @Override
    public Optional<MeResult> beginOnboarding(
            UUID operationId, UUID userId, WorkflowOperationType type, String fingerprint, Instant now) {
        requireOnboardingType(type);
        return begin(operationId, userId.toString(), type, fingerprint, now).map(row -> {
            MeResult result = json.readValue(row.getResultPayload().data(), MeResult.class);
            if (!result.user().id().equals(userId)) {
                throw new IllegalStateException("Stored onboarding result belongs to another account");
            }
            return result;
        });
    }

    @Override
    public void completeOnboarding(
            UUID operationId, UUID userId, WorkflowOperationType type, String fingerprint, MeResult result, Instant now) {
        requireOnboardingType(type);
        if (!result.user().id().equals(userId)) {
            throw new IllegalStateException("Onboarding result does not match its operation context");
        }
        complete(operationId, userId.toString(), type, fingerprint, json.writeValueAsString(result), now);
    }

    private Optional<WorkflowsOperationsRecord> begin(
            UUID operationId, String context, WorkflowOperationType type, String fingerprint, Instant now) {
        lock(operationId);
        Optional<WorkflowsOperationsRecord> existing = dsl.selectFrom(WORKFLOWS_OPERATIONS)
                .where(WORKFLOWS_OPERATIONS.OPERATION_ID.eq(operationId)).forUpdate().fetchOptional();
        if (existing.isEmpty()) {
            dsl.insertInto(WORKFLOWS_OPERATIONS)
                    .set(WORKFLOWS_OPERATIONS.OPERATION_ID, operationId)
                    .set(WORKFLOWS_OPERATIONS.OPERATION_TYPE, type.name())
                    .set(WORKFLOWS_OPERATIONS.CONTEXT_KEY, context)
                    .set(WORKFLOWS_OPERATIONS.PAYLOAD_FINGERPRINT, fingerprint)
                    .set(WORKFLOWS_OPERATIONS.FINGERPRINT_VERSION, 1)
                    .set(WORKFLOWS_OPERATIONS.RESULT_SCHEMA_VERSION, 1)
                    .set(WORKFLOWS_OPERATIONS.CREATED_AT, now.atOffset(ZoneOffset.UTC)).execute();
            return Optional.empty();
        }

        WorkflowsOperationsRecord row = existing.get();
        if (!row.getOperationType().equals(type.name()) || !row.getContextKey().equals(context)
                || !row.getPayloadFingerprint().equals(fingerprint)) {
            throw new WorkflowException(WorkflowException.Reason.IDEMPOTENCY_CONFLICT);
        }
        if (row.getFingerprintVersion() != 1 || row.getResultSchemaVersion() != 1
                || row.getCompletedAt() == null || row.getResultPayload() == null) {
            throw new IllegalStateException("Stored workflow operation is incomplete or unsupported");
        }
        return existing;
    }

    private void complete(
            UUID operationId, String context, WorkflowOperationType type, String fingerprint, String result, Instant now) {
        int changed = dsl.update(WORKFLOWS_OPERATIONS)
                .set(WORKFLOWS_OPERATIONS.RESULT_PAYLOAD, JSONB.valueOf(result))
                .set(WORKFLOWS_OPERATIONS.COMPLETED_AT, now.atOffset(ZoneOffset.UTC))
                .where(WORKFLOWS_OPERATIONS.OPERATION_ID.eq(operationId))
                .and(WORKFLOWS_OPERATIONS.OPERATION_TYPE.eq(type.name()))
                .and(WORKFLOWS_OPERATIONS.CONTEXT_KEY.eq(context))
                .and(WORKFLOWS_OPERATIONS.PAYLOAD_FINGERPRINT.eq(fingerprint))
                .and(WORKFLOWS_OPERATIONS.COMPLETED_AT.isNull()).execute();
        if (changed != 1) {
            throw new IllegalStateException("Workflow operation was not started or already completed");
        }
    }

    private void lock(UUID operationId) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(("workflows:operation:" + operationId).getBytes(StandardCharsets.UTF_8));
            dsl.fetch("select pg_advisory_xact_lock(?)", ByteBuffer.wrap(hash).getLong());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void requireOnboardingType(WorkflowOperationType type) {
        if (type == null || type == WorkflowOperationType.REGISTER) {
            throw new IllegalArgumentException("Expected an onboarding operation");
        }
    }
}

