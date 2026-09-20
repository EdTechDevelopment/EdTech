CREATE TABLE notification_email_deliveries
(
    id               UUID         NOT NULL,
    recipient_email  VARCHAR(254) NOT NULL,
    confirmation_url TEXT,
    purpose          VARCHAR(32)  NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    expires_at       TIMESTAMPTZ  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    sent_at          TIMESTAMPTZ,

    CONSTRAINT pk_notification_email_deliveries PRIMARY KEY (id),
    CONSTRAINT ck_notification_email_deliveries_purpose CHECK (purpose IN ('REGISTRATION', 'EMAIL_CHANGE')),
    CONSTRAINT ck_notification_email_deliveries_status CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED', 'EXPIRED')),
    CONSTRAINT ck_notification_email_deliveries_expiration CHECK (expires_at > created_at),
    CONSTRAINT ck_notification_email_deliveries_update_time CHECK (updated_at >= created_at),
    CONSTRAINT ck_notification_email_deliveries_payload CHECK (
        (status IN ('PENDING', 'PROCESSING') AND confirmation_url IS NOT NULL)
            OR (status IN ('SENT', 'FAILED', 'EXPIRED') AND confirmation_url IS NULL)
    ),
    CONSTRAINT ck_notification_email_deliveries_sent_time CHECK (
        (status = 'SENT' AND sent_at IS NOT NULL)
            OR (status <> 'SENT' AND sent_at IS NULL)
    )
);
