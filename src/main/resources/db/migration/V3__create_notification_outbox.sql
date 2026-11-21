
-- ============================================================
-- V3: Transactional notification outbox
-- ============================================================

CREATE TABLE notification_outbox
(
    id                  UUID         NOT NULL,
    delivery_id         UUID         NOT NULL,
    message_id          UUID         NOT NULL,
    event_type          VARCHAR(100) NOT NULL,
    exchange_name       VARCHAR(255) NOT NULL,
    routing_key         VARCHAR(255) NOT NULL,
    payload             TEXT         NOT NULL,

    status              VARCHAR(30)  NOT NULL DEFAULT 'PENDING',

    attempt_count       INTEGER      NOT NULL DEFAULT 0,
    max_attempts        INTEGER      NOT NULL DEFAULT 10,

    available_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    locked_until        TIMESTAMPTZ,
    locked_by           VARCHAR(150),

    published_at        TIMESTAMPTZ,
    last_error          TEXT,

    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_notification_outbox
        PRIMARY KEY (id),

    CONSTRAINT fk_notification_outbox_delivery
        FOREIGN KEY (delivery_id)
        REFERENCES notification_deliveries (id)
        ON DELETE CASCADE,

    CONSTRAINT uq_notification_outbox_delivery
        UNIQUE (delivery_id),

    CONSTRAINT uq_notification_outbox_message
        UNIQUE (message_id),

    CONSTRAINT chk_notification_outbox_status
        CHECK (
            status IN (
                'PENDING',
                'PROCESSING',
                'RETRY_SCHEDULED',
                'PUBLISHED',
                'DEAD_LETTERED'
            )
        ),

    CONSTRAINT chk_notification_outbox_attempts
        CHECK (
            attempt_count >= 0
            AND max_attempts >= 1
            AND attempt_count <= max_attempts
        ),

    CONSTRAINT chk_notification_outbox_version
        CHECK (version >= 0),

    CONSTRAINT chk_notification_outbox_payload
        CHECK (LENGTH(TRIM(payload)) > 0),

    CONSTRAINT chk_notification_outbox_event_type
        CHECK (LENGTH(TRIM(event_type)) > 0),

    CONSTRAINT chk_notification_outbox_exchange
        CHECK (LENGTH(TRIM(exchange_name)) > 0),

    CONSTRAINT chk_notification_outbox_routing_key
        CHECK (LENGTH(TRIM(routing_key)) > 0),

    CONSTRAINT chk_notification_outbox_lease
        CHECK (
            (
                status = 'PROCESSING'
                AND locked_until IS NOT NULL
                AND locked_by IS NOT NULL
            )
            OR
            (
                status <> 'PROCESSING'
                AND locked_until IS NULL
                AND locked_by IS NULL
            )
        ),

    CONSTRAINT chk_notification_outbox_published_at
        CHECK (
            (status = 'PUBLISHED' AND published_at IS NOT NULL)
            OR status <> 'PUBLISHED'
        )
);

-- Efficiently locate pending and retryable records.
CREATE INDEX idx_notification_outbox_available
    ON notification_outbox (
        status,
        available_at,
        created_at
    );

-- Efficiently locate expired worker leases.
CREATE INDEX idx_notification_outbox_expired_lease
    ON notification_outbox (
        status,
        locked_until
    )
    WHERE status = 'PROCESSING';

-- Efficiently monitor publishing outcomes.
CREATE INDEX idx_notification_outbox_created
    ON notification_outbox (created_at DESC);

COMMENT ON TABLE notification_outbox IS
    'Transactional outbox used to reliably publish notification delivery requests to RabbitMQ.';

COMMENT ON COLUMN notification_outbox.delivery_id IS
    'The notification delivery record associated with this outbox message.';

COMMENT ON COLUMN notification_outbox.message_id IS
    'Stable message identifier used for RabbitMQ message deduplication.';

COMMENT ON COLUMN notification_outbox.payload IS
    'Serialized delivery request. Must not contain decrypted sensitive notification content.';

COMMENT ON COLUMN notification_outbox.locked_until IS
    'Lease expiry time for the worker currently processing this record.';

COMMENT ON COLUMN notification_outbox.published_at IS
    'Timestamp when RabbitMQ acknowledged successful publication.';