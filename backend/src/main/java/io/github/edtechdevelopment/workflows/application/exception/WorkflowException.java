package io.github.edtechdevelopment.workflows.application.exception;

import java.util.Objects;
import java.util.Set;

public class WorkflowException extends RuntimeException {

    public enum Reason {
        ROLE_PROFILE_MISMATCH,
        ACCOUNT_UNAVAILABLE,
        ACCOUNT_NOT_ACTIVE,
        ROLE_ALREADY_ENABLED,
        PROFILE_INCONSISTENCY,
        IDEMPOTENCY_CONFLICT
    }

    private final Reason reason;
    private final Set<String> invalidFields;

    public WorkflowException(Reason reason) {
        this(reason, Set.of());
    }

    public WorkflowException(Reason reason, Set<String> invalidFields) {
        super(reason.name());
        this.reason = Objects.requireNonNull(reason, "Failure reason must not be null");
        this.invalidFields = Set.copyOf(invalidFields);
    }

    public Reason reason() {
        return reason;
    }

    public Set<String> invalidFields() {
        return invalidFields;
    }
}

