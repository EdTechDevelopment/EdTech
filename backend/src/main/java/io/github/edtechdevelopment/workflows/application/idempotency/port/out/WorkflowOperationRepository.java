package io.github.edtechdevelopment.workflows.application.idempotency.port.out;

import io.github.edtechdevelopment.workflows.application.idempotency.model.WorkflowOperationType;
import io.github.edtechdevelopment.workflows.application.me.model.MeResult;
import io.github.edtechdevelopment.workflows.application.registration.model.RegistrationOutcome;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowOperationRepository {

    Optional<RegistrationOutcome> beginRegistration(UUID operationId, String email, String fingerprint, Instant now);

    void completeRegistration(UUID operationId, String email, String fingerprint, RegistrationOutcome result, Instant now);

    Optional<MeResult> beginOnboarding(
            UUID operationId, UUID userId, WorkflowOperationType type, String fingerprint, Instant now);

    void completeOnboarding(
            UUID operationId, UUID userId, WorkflowOperationType type, String fingerprint, MeResult result, Instant now);
}

