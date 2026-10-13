CREATE TABLE identity_account_email_event_outbox
(
    event_id        UUID         NOT NULL,
    user_id         UUID         NOT NULL,
    email           VARCHAR(254) NOT NULL,
    purpose         VARCHAR(32)  NOT NULL,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    next_attempt_at TIMESTAMPTZ  NOT NULL,
    published_at    TIMESTAMPTZ,

    CONSTRAINT pk_identity_account_email_event_outbox PRIMARY KEY (event_id),
    CONSTRAINT ck_identity_account_email_event_outbox_purpose CHECK (purpose IN ('REGISTRATION', 'EMAIL_CHANGE'))
);

CREATE INDEX idx_identity_account_email_event_outbox_pending
    ON identity_account_email_event_outbox (next_attempt_at, event_id) WHERE published_at IS NULL;

CREATE TABLE notification_invitation_email_deliveries
(
    id                   UUID         NOT NULL,
    invitation_id        UUID         NOT NULL,
    deduplication_key     TEXT         NOT NULL,
    recipient_email      VARCHAR(254) NOT NULL,
    teacher_display_name TEXT         NOT NULL,
    invitation_url       TEXT,
    status               VARCHAR(16)  NOT NULL,
    expires_at           TIMESTAMPTZ  NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL,
    available_at         TIMESTAMPTZ  NOT NULL,
    claim_id             UUID,
    claimed_at           TIMESTAMPTZ,
    sent_at              TIMESTAMPTZ,

    CONSTRAINT pk_notification_invitation_email_deliveries PRIMARY KEY (id),
    CONSTRAINT uq_notification_invitation_email_deliveries_key UNIQUE (deduplication_key),
    CONSTRAINT uq_notification_invitation_email_deliveries_invitation UNIQUE (invitation_id),
    CONSTRAINT ck_notification_invitation_email_deliveries_status CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED', 'EXPIRED')),
    CONSTRAINT ck_notification_invitation_email_deliveries_expiration CHECK (expires_at > created_at),
    CONSTRAINT ck_notification_invitation_email_deliveries_claim CHECK (
        (status = 'PROCESSING' AND claim_id IS NOT NULL AND claimed_at IS NOT NULL)
        OR (status <> 'PROCESSING' AND claim_id IS NULL AND claimed_at IS NULL)
    ),
    CONSTRAINT ck_notification_invitation_email_deliveries_payload CHECK (
        (status IN ('PENDING', 'PROCESSING') AND invitation_url IS NOT NULL)
        OR (status IN ('SENT', 'FAILED', 'EXPIRED') AND invitation_url IS NULL)
    ),
    CONSTRAINT ck_notification_invitation_email_deliveries_sent CHECK (
        (status = 'SENT' AND sent_at IS NOT NULL) OR (status <> 'SENT' AND sent_at IS NULL)
    )
);

CREATE INDEX idx_notification_invitation_email_deliveries_pending
    ON notification_invitation_email_deliveries (available_at, id) WHERE status = 'PENDING';
CREATE INDEX idx_notification_invitation_email_deliveries_processing
    ON notification_invitation_email_deliveries (claimed_at) WHERE status = 'PROCESSING';
CREATE INDEX idx_notification_invitation_email_deliveries_expiration
    ON notification_invitation_email_deliveries (expires_at) WHERE status IN ('PENDING', 'PROCESSING');
