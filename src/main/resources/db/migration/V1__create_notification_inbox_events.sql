-- ============================================================
-- V1: Create notification inbox events
-- ============================================================

CREATE TABLE notification_inbox_events
(
    id                  UUID         NOT NULL,

    event_type          VARCHAR(100) NOT NULL,
    message_id          VARCHAR(255) NOT NULL,
    user_id             UUID         NOT NULL,

    encrypted_payload   TEXT         NOT NULL,

    status              VARCHAR(20)  NOT NULL DEFAULT 'RECEIVED',

    attempts            INTEGER      NOT NULL DEFAULT 0,

    received_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at        TIMESTAMPTZ,

    last_error          VARCHAR(150),

    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_notification_inbox_events
        PRIMARY KEY (id),

    CONSTRAINT uq_notification_inbox_event_identity
        UNIQUE (event_type, message_id),

    CONSTRAINT chk_notification_inbox_status
        CHECK (
            status IN (
                'RECEIVED',
                'PROCESSING',
                'PROCESSED',
                'FAILED'
            )
        ),

    CONSTRAINT chk_notification_inbox_attempts
        CHECK (attempts >= 0)
);

-- ============================================================
-- Indexes
-- ============================================================

CREATE INDEX idx_notification_inbox_status_received
    ON notification_inbox_events (status, received_at);

CREATE INDEX idx_notification_inbox_user
    ON notification_inbox_events (user_id);

-- ============================================================
-- Documentation
-- ============================================================

COMMENT ON TABLE notification_inbox_events IS
    'Durable inbox for incoming events from Identity Service.';

COMMENT ON COLUMN notification_inbox_events.message_id IS
    'Original RabbitMQ message ID supplied by the Identity Service outbox relay.';

COMMENT ON COLUMN notification_inbox_events.encrypted_payload IS
    'AES-256-GCM encrypted JSON event payload.';

COMMENT ON COLUMN notification_inbox_events.status IS
    'Inbox processing state. RECEIVED means durably accepted but not yet processed.';