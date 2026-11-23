-- ============================================================
-- V5: Notification delivery worker leases
-- ============================================================

ALTER TABLE notification_deliveries
    ADD COLUMN locked_until TIMESTAMPTZ,
    ADD COLUMN locked_by VARCHAR(150);


-- ============================================================
-- Recover any PROCESSING rows from an older implementation.
--
-- These rows did not have lease information, so they cannot
-- be safely treated as currently owned by a worker.
-- ============================================================

UPDATE notification_deliveries
SET
    status = 'RETRY_SCHEDULED',
    next_attempt_at = CURRENT_TIMESTAMP,
    last_error = COALESCE(
        last_error,
        'Recovered during notification delivery lease migration.'
    )
WHERE status = 'PROCESSING';


-- ============================================================
-- A PROCESSING delivery must have a lease owner and expiry.
-- All other states must have neither.
-- ============================================================

ALTER TABLE notification_deliveries
    ADD CONSTRAINT chk_notification_delivery_lease
    CHECK (
        (
            status = 'PROCESSING'
            AND locked_until IS NOT NULL
            AND locked_by IS NOT NULL
            AND LENGTH(TRIM(locked_by)) > 0
        )
        OR
        (
            status <> 'PROCESSING'
            AND locked_until IS NULL
            AND locked_by IS NULL
        )
    );


-- ============================================================
-- Due delivery lookup
-- ============================================================

CREATE INDEX idx_notification_delivery_due_email
    ON notification_deliveries (
        status,
        next_attempt_at,
        created_at
    )
    WHERE channel = 'EMAIL'
      AND status IN ('PENDING', 'RETRY_SCHEDULED');


-- ============================================================
-- Expired delivery leases
-- ============================================================

CREATE INDEX idx_notification_delivery_expired_lease
    ON notification_deliveries (
        locked_until
    )
    WHERE channel = 'EMAIL'
      AND status = 'PROCESSING';