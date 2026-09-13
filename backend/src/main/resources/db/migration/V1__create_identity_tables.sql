CREATE TABLE identity_users
(
    id                UUID         NOT NULL,
    password_hash     VARCHAR(100) NOT NULL,
    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    status            VARCHAR(40)  NOT NULL,
    email_verified_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_identity_users PRIMARY KEY (id),
    CONSTRAINT ck_identity_users_status CHECK (status IN ('PENDING_EMAIL_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DEACTIVATED'))
);


CREATE TABLE identity_user_emails
(
    user_id UUID         NOT NULL,
    email   VARCHAR(254) NOT NULL,
    kind    VARCHAR(16)  NOT NULL,

    CONSTRAINT pk_identity_user_emails PRIMARY KEY (user_id, kind),
    CONSTRAINT uq_identity_user_emails_email UNIQUE (email),
    CONSTRAINT fk_identity_user_emails_user FOREIGN KEY (user_id) REFERENCES identity_users (id) ON DELETE CASCADE,
    CONSTRAINT ck_identity_user_emails_email_normalized CHECK (email = lower(email)),
    CONSTRAINT ck_identity_user_emails_kind CHECK (kind IN ('CURRENT', 'PENDING'))
);

CREATE TABLE identity_user_roles
(
    user_id UUID        NOT NULL,
    role    VARCHAR(16) NOT NULL,

    CONSTRAINT pk_identity_user_roles PRIMARY KEY (user_id, role),
    CONSTRAINT fk_identity_user_roles_user FOREIGN KEY (user_id) REFERENCES identity_users (id) ON DELETE CASCADE,
    CONSTRAINT ck_identity_user_roles_role CHECK (role IN ('TEACHER', 'STUDENT'))
);

CREATE TABLE identity_email_verifications
(
    id             UUID         NOT NULL,
    user_id        UUID         NOT NULL,
    target_email   VARCHAR(254) NOT NULL,
    token_hash     VARCHAR(64)  NOT NULL,
    purpose        VARCHAR(32)  NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    consumed_at    TIMESTAMPTZ,
    invalidated_at TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_identity_email_verifications PRIMARY KEY (id),
    CONSTRAINT uq_identity_email_verifications_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_identity_email_verifications_user FOREIGN KEY (user_id) REFERENCES identity_users (id) ON DELETE CASCADE,
    CONSTRAINT ck_identity_email_verifications_target_email_normalized CHECK (target_email = lower(target_email)),
    CONSTRAINT ck_identity_email_verifications_purpose CHECK (purpose IN ('REGISTRATION', 'EMAIL_CHANGE')),
    CONSTRAINT ck_identity_email_verifications_expiration CHECK (expires_at > created_at)
);

CREATE INDEX idx_identity_email_verifications_user_purpose ON identity_email_verifications (user_id, purpose);
CREATE INDEX idx_identity_email_verifications_expires_at ON identity_email_verifications (expires_at);

CREATE TABLE identity_refresh_tokens
(
    id         UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    family_id  UUID        NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_identity_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_identity_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_identity_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES identity_users (id) ON DELETE CASCADE,
    CONSTRAINT ck_identity_refresh_tokens_expiration CHECK (expires_at > created_at)
);

CREATE INDEX idx_identity_refresh_tokens_user_id_family_id ON identity_refresh_tokens (user_id, family_id);
CREATE INDEX idx_identity_refresh_tokens_expires_at ON identity_refresh_tokens (expires_at);
