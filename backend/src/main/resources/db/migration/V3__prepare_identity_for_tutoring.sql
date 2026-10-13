ALTER TABLE identity_users
    ADD COLUMN birth_date DATE NOT NULL;

CREATE TABLE identity_command_operations
(
    operation_id            UUID         NOT NULL,
    operation_type          VARCHAR(32)  NOT NULL,
    payload_fingerprint     VARCHAR(64)  NOT NULL,
    registration_password_hash VARCHAR(100),
    user_id                 UUID,
    verification_expires_at TIMESTAMPTZ,

    CONSTRAINT pk_identity_command_operations PRIMARY KEY (operation_id),
    CONSTRAINT ck_identity_command_operations_type CHECK (operation_type IN ('REGISTER', 'ADD_ROLE')),
    CONSTRAINT fk_identity_command_operations_user FOREIGN KEY (user_id) REFERENCES identity_users (id),
    CONSTRAINT ck_identity_command_operations_result CHECK (
        (operation_type = 'REGISTER' AND registration_password_hash IS NOT NULL)
        OR (operation_type = 'ADD_ROLE' AND registration_password_hash IS NULL)
    )
);
