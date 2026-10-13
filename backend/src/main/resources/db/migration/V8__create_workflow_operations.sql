CREATE TABLE workflows_operations
(
    operation_id          UUID         NOT NULL,
    operation_type        VARCHAR(32)  NOT NULL,
    context_key           VARCHAR(254) NOT NULL,
    payload_fingerprint   CHAR(64)     NOT NULL,
    fingerprint_version   INTEGER      NOT NULL,
    result_schema_version INTEGER      NOT NULL,
    result_payload        JSONB,
    created_at            TIMESTAMPTZ  NOT NULL,
    completed_at          TIMESTAMPTZ,

    CONSTRAINT pk_workflows_operations PRIMARY KEY (operation_id),
    CONSTRAINT ck_workflows_operations_type CHECK (
        operation_type IN ('REGISTER', 'ADD_TEACHER_ROLE', 'ADD_STUDENT_ROLE')
    ),
    CONSTRAINT ck_workflows_operations_context CHECK (length(btrim(context_key)) > 0),
    CONSTRAINT ck_workflows_operations_fingerprint CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_workflows_operations_versions CHECK (
        fingerprint_version > 0 AND result_schema_version > 0
    ),
    CONSTRAINT ck_workflows_operations_result CHECK (
        (result_payload IS NULL AND completed_at IS NULL)
        OR (result_payload IS NOT NULL AND completed_at IS NOT NULL AND jsonb_typeof(result_payload) = 'object')
    ),
    CONSTRAINT ck_workflows_operations_completion CHECK (completed_at IS NULL OR completed_at >= created_at)
);
