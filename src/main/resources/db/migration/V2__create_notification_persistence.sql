-- ============================================================
-- V2: Notification persistence
-- ============================================================
--
-- Creates:
-- 1. notifications
-- 2. notification_templates
-- 3. notification_preferences
-- 4. notification_deliveries
--
-- Existing V1 notification_inbox_events remains unchanged.
-- ============================================================


-- ============================================================
-- 1. NOTIFICATION TEMPLATES
-- ============================================================

CREATE TABLE notification_templates
(
    id                  UUID         NOT NULL,

    template_key        VARCHAR(120) NOT NULL,

    channel             VARCHAR(20)  NOT NULL,

    locale              VARCHAR(20)  NOT NULL DEFAULT 'en-NG',

    version             INTEGER      NOT NULL,

    active              BOOLEAN      NOT NULL DEFAULT FALSE,

    subject_template    VARCHAR(500),

    title_template      VARCHAR(500),

    body_template       TEXT         NOT NULL,

    content_format      VARCHAR(20)  NOT NULL DEFAULT 'PLAIN_TEXT',

    description         VARCHAR(500),

    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,


    CONSTRAINT pk_notification_templates
        PRIMARY KEY (id),


    CONSTRAINT uq_notification_template_version
        UNIQUE (
            template_key,
            channel,
            locale,
            version
        ),


    CONSTRAINT chk_notification_template_channel
        CHECK (
            channel IN (
                'EMAIL',
                'IN_APP',
                'SMS',
                'PUSH'
            )
        ),


    CONSTRAINT chk_notification_template_content_format
        CHECK (
            content_format IN (
                'PLAIN_TEXT',
                'HTML'
            )
        ),


    CONSTRAINT chk_notification_template_version
        CHECK (version >= 1),


    CONSTRAINT chk_notification_template_key
        CHECK (LENGTH(TRIM(template_key)) > 0),


    CONSTRAINT chk_notification_template_body
        CHECK (LENGTH(TRIM(body_template)) > 0)
);


-- Only one active template per key, channel and locale.
--
-- Older versions remain in the database but inactive.

CREATE UNIQUE INDEX uq_notification_template_active
    ON notification_templates (
        template_key,
        channel,
        locale
    )
    WHERE active = TRUE;


CREATE INDEX idx_notification_template_lookup
    ON notification_templates (
        template_key,
        channel,
        locale,
        active
    );


COMMENT ON TABLE notification_templates IS
    'Versioned notification templates for email, in-app, SMS and push channels.';

COMMENT ON COLUMN notification_templates.template_key IS
    'Stable application identifier for a notification template.';

COMMENT ON COLUMN notification_templates.version IS
    'Immutable template version number.';

COMMENT ON COLUMN notification_templates.active IS
    'Indicates the currently active version for a template key, channel and locale.';



-- ============================================================
-- 2. NOTIFICATION PREFERENCES
-- ============================================================

CREATE TABLE notification_preferences
(
    id                      UUID         NOT NULL,

    user_id                 UUID         NOT NULL,

    category                VARCHAR(30)  NOT NULL,

    channel                 VARCHAR(20)  NOT NULL,

    enabled                 BOOLEAN      NOT NULL DEFAULT TRUE,

    quiet_hours_enabled     BOOLEAN      NOT NULL DEFAULT FALSE,

    quiet_hours_start       TIME,

    quiet_hours_end         TIME,

    time_zone               VARCHAR(64)  NOT NULL DEFAULT 'Africa/Lagos',

    created_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    version                 BIGINT       NOT NULL DEFAULT 0,


    CONSTRAINT pk_notification_preferences
        PRIMARY KEY (id),


    CONSTRAINT uq_notification_preference_user_category_channel
        UNIQUE (
            user_id,
            category,
            channel
        ),


    CONSTRAINT chk_notification_preference_category
        CHECK (
            category IN (
                'SECURITY',
                'ACCOUNT',
                'TRANSACTION',
                'RISK',
                'SYSTEM',
                'MARKETING'
            )
        ),


    CONSTRAINT chk_notification_preference_channel
        CHECK (
            channel IN (
                'EMAIL',
                'IN_APP',
                'SMS',
                'PUSH'
            )
        ),


    CONSTRAINT chk_notification_preference_quiet_hours
        CHECK (
            (
                quiet_hours_enabled = FALSE
                AND quiet_hours_start IS NULL
                AND quiet_hours_end IS NULL
            )
            OR
            (
                quiet_hours_enabled = TRUE
                AND quiet_hours_start IS NOT NULL
                AND quiet_hours_end IS NOT NULL
                AND quiet_hours_start <> quiet_hours_end
            )
        ),


    CONSTRAINT chk_notification_preference_version
        CHECK (version >= 0),


    CONSTRAINT chk_notification_preference_timezone
        CHECK (LENGTH(TRIM(time_zone)) > 0)
);


CREATE INDEX idx_notification_preferences_user
    ON notification_preferences (user_id);


CREATE INDEX idx_notification_preferences_user_enabled
    ON notification_preferences (
        user_id,
        enabled
    );


COMMENT ON TABLE notification_preferences IS
    'User-specific notification channel preferences and quiet-hour settings.';

COMMENT ON COLUMN notification_preferences.user_id IS
    'UUID of the user owned by Identity Service.';

COMMENT ON COLUMN notification_preferences.time_zone IS
    'IANA time zone used to interpret quiet hours.';



-- ============================================================
-- 3. NOTIFICATIONS
-- ============================================================

CREATE TABLE notifications
(
    id                      UUID         NOT NULL,

    user_id                 UUID         NOT NULL,

    notification_type       VARCHAR(60)  NOT NULL,

    category                VARCHAR(30)  NOT NULL,

    source_event_type       VARCHAR(100),

    source_message_id       VARCHAR(255),

    source_inbox_event_id   UUID,

    title                   VARCHAR(255) NOT NULL,

    body                    TEXT         NOT NULL,

    action_url              VARCHAR(1000),

    metadata_json           TEXT,

    created_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    expires_at              TIMESTAMPTZ,

    read_at                 TIMESTAMPTZ,

    archived_at             TIMESTAMPTZ,

    version                 BIGINT       NOT NULL DEFAULT 0,


    CONSTRAINT pk_notifications
        PRIMARY KEY (id),


    CONSTRAINT uq_notification_source_event
        UNIQUE (
            user_id,
            source_event_type,
            source_message_id
        ),


    CONSTRAINT fk_notification_source_inbox_event
        FOREIGN KEY (source_inbox_event_id)
        REFERENCES notification_inbox_events (id)
        ON DELETE SET NULL,


    CONSTRAINT chk_notification_category
        CHECK (
            category IN (
                'SECURITY',
                'ACCOUNT',
                'TRANSACTION',
                'RISK',
                'SYSTEM',
                'MARKETING'
            )
        ),


    CONSTRAINT chk_notification_type
        CHECK (
            notification_type IN (
                'EMAIL_VERIFICATION',
                'PASSWORD_RESET',
                'STAFF_ONBOARDING',
                'CUSTOMER_CONVERTED_TO_STAFF',
                'EMAIL_CHANGE_VERIFICATION',
                'EMAIL_CHANGED',
                'ACCOUNT_CREATED',
                'ACCOUNT_UPDATED',
                'ACCOUNT_RESTRICTED',
                'ACCOUNT_CLOSED',
                'TRANSACTION_SUCCESSFUL',
                'TRANSACTION_FAILED',
                'TRANSACTION_REVERSED',
                'SUSPICIOUS_ACTIVITY',
                'RISK_ALERT',
                'ACCOUNT_SECURITY_ALERT',
                'SYSTEM_ANNOUNCEMENT',
                'GENERAL'
            )
        ),


    CONSTRAINT chk_notification_source_event_pair
        CHECK (
            (
                source_event_type IS NULL
                AND source_message_id IS NULL
            )
            OR
            (
                source_event_type IS NOT NULL
                AND source_message_id IS NOT NULL
            )
        ),


    CONSTRAINT chk_notification_title
        CHECK (LENGTH(TRIM(title)) > 0),


    CONSTRAINT chk_notification_body
        CHECK (LENGTH(TRIM(body)) > 0),


    CONSTRAINT chk_notification_version
        CHECK (version >= 0)
);


CREATE INDEX idx_notifications_user_created
    ON notifications (
        user_id,
        created_at DESC
    );


CREATE INDEX idx_notifications_user_read
    ON notifications (
        user_id,
        read_at
    );


CREATE INDEX idx_notifications_user_category
    ON notifications (
        user_id,
        category
    );


CREATE INDEX idx_notifications_expiry
    ON notifications (
        expires_at
    )
    WHERE expires_at IS NOT NULL;


CREATE INDEX idx_notifications_source_inbox_event
    ON notifications (
        source_inbox_event_id
    )
    WHERE source_inbox_event_id IS NOT NULL;


COMMENT ON TABLE notifications IS
    'User-facing notifications generated from identity, account, transaction, risk and system events.';

COMMENT ON COLUMN notifications.user_id IS
    'UUID of the notification recipient from Identity Service.';

COMMENT ON COLUMN notifications.source_inbox_event_id IS
    'Optional reference to the original durable inbox event.';

COMMENT ON COLUMN notifications.metadata_json IS
    'Optional serialized JSON metadata associated with the notification.';

COMMENT ON COLUMN notifications.read_at IS
    'Timestamp when the user read the notification. NULL means unread.';

COMMENT ON COLUMN notifications.archived_at IS
    'Timestamp when the notification was archived.';



-- ============================================================
-- 4. NOTIFICATION DELIVERIES
-- ============================================================

CREATE TABLE notification_deliveries
(
    id                      UUID         NOT NULL,

    notification_id         UUID         NOT NULL,

    template_id             UUID,

    channel                 VARCHAR(20)  NOT NULL,

    status                  VARCHAR(30)  NOT NULL DEFAULT 'PENDING',

    encrypted_destination   TEXT,

    subject                 VARCHAR(500),

    encrypted_content       TEXT         NOT NULL,

    content_format          VARCHAR(20)  NOT NULL DEFAULT 'PLAIN_TEXT',

    provider                VARCHAR(80),

    provider_message_id     VARCHAR(255),

    idempotency_key         VARCHAR(255),

    attempt_count            INTEGER      NOT NULL DEFAULT 0,

    max_attempts             INTEGER      NOT NULL DEFAULT 5,

    next_attempt_at         TIMESTAMPTZ,

    last_attempt_at         TIMESTAMPTZ,

    sent_at                 TIMESTAMPTZ,

    delivered_at            TIMESTAMPTZ,

    failed_at               TIMESTAMPTZ,

    last_error              TEXT,

    created_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    version                 BIGINT       NOT NULL DEFAULT 0,


    CONSTRAINT pk_notification_deliveries
        PRIMARY KEY (id),


    CONSTRAINT fk_notification_delivery_notification
        FOREIGN KEY (notification_id)
        REFERENCES notifications (id)
        ON DELETE CASCADE,


    CONSTRAINT fk_notification_delivery_template
        FOREIGN KEY (template_id)
        REFERENCES notification_templates (id)
        ON DELETE RESTRICT,


    CONSTRAINT uq_notification_delivery_channel
        UNIQUE (
            notification_id,
            channel
        ),


    CONSTRAINT uq_notification_delivery_idempotency
        UNIQUE (idempotency_key),


    CONSTRAINT chk_notification_delivery_channel
        CHECK (
            channel IN (
                'EMAIL',
                'IN_APP',
                'SMS',
                'PUSH'
            )
        ),


    CONSTRAINT chk_notification_delivery_status
        CHECK (
            status IN (
                'PENDING',
                'PROCESSING',
                'RETRY_SCHEDULED',
                'SENT',
                'DELIVERED',
                'FAILED',
                'DEAD_LETTERED',
                'CANCELLED'
            )
        ),


    CONSTRAINT chk_notification_delivery_content_format
        CHECK (
            content_format IN (
                'PLAIN_TEXT',
                'HTML'
            )
        ),


    CONSTRAINT chk_notification_delivery_attempts
        CHECK (
            attempt_count >= 0
            AND max_attempts >= 1
            AND attempt_count <= max_attempts
        ),


    CONSTRAINT chk_notification_delivery_version
        CHECK (version >= 0),


    CONSTRAINT chk_notification_delivery_content
        CHECK (LENGTH(TRIM(encrypted_content)) > 0)
);


CREATE INDEX idx_notification_delivery_status_next_attempt
    ON notification_deliveries (
        status,
        next_attempt_at
    );


CREATE INDEX idx_notification_delivery_notification
    ON notification_deliveries (
        notification_id
    );


CREATE INDEX idx_notification_delivery_provider_message
    ON notification_deliveries (
        provider_message_id
    )
    WHERE provider_message_id IS NOT NULL;


CREATE INDEX idx_notification_delivery_created
    ON notification_deliveries (
        created_at
    );


COMMENT ON TABLE notification_deliveries IS
    'Per-channel delivery records, provider responses, attempt tracking and delivery lifecycle.';

COMMENT ON COLUMN notification_deliveries.encrypted_destination IS
        'AES-256-GCM encrypted destination snapshot.';

COMMENT ON COLUMN notification_deliveries.encrypted_content IS
        'AES-256-GCM encrypted rendered delivery content.';

COMMENT ON COLUMN notification_deliveries.provider_message_id IS
    'Message identifier returned by the external delivery provider.';

COMMENT ON COLUMN notification_deliveries.idempotency_key IS
    'Unique identifier used to prevent duplicate delivery creation.';

COMMENT ON COLUMN notification_deliveries.attempt_count IS
    'Number of delivery attempts started for this record.';


-- ============================================================
-- 5. DEFAULT IDENTITY EMAIL TEMPLATES
-- ============================================================

INSERT INTO notification_templates (
    id,
    template_key,
    channel,
    locale,
    version,
    active,
    subject_template,
    title_template,
    body_template,
    content_format,
    description
)
VALUES

(
    gen_random_uuid(),
    'identity.email-verification',
    'EMAIL',
    'en-NG',
    1,
    TRUE,
    'Verify your Gideon Heritage Bank email address',
    'Verify your email address',
    '<html><body><h2>Hello {{firstName}},</h2><p>Thank you for registering with Gideon Heritage Bank.</p><p>Please verify your email address using the link below.</p><p><a href="{{verificationUrl}}">Verify email address</a></p><p>This link expires at {{expiresAt}}.</p><p>If you did not create this account, please contact the bank.</p></body></html>',
    'HTML',
    'Customer email verification message.'
),

(
    gen_random_uuid(),
    'identity.password-reset',
    'EMAIL',
    'en-NG',
    1,
    TRUE,
    'Reset your Gideon Heritage Bank password',
    'Password reset requested',
    '<html><body><h2>Hello {{firstName}},</h2><p>We received a request to reset your banking password.</p><p><a href="{{resetUrl}}">Reset your password</a></p><p>This link expires at {{expiresAt}}.</p><p>If you did not request this reset, please ignore this message and secure your account.</p></body></html>',
    'HTML',
    'Password reset email.'
),

(
    gen_random_uuid(),
    'identity.staff-onboarding',
    'EMAIL',
    'en-NG',
    1,
    TRUE,
    'Welcome to Gideon Heritage Bank',
    'Staff onboarding',
    '<html><body><h2>Welcome {{firstName}},</h2><p>Your staff identity has been created.</p><p>Please complete your onboarding using the link below.</p><p><a href="{{onboardingUrl}}">Complete onboarding</a></p><p>This link expires at {{expiresAt}}.</p></body></html>',
    'HTML',
    'Staff onboarding email.'
),

(
    gen_random_uuid(),
    'identity.customer-converted-to-staff',
    'EMAIL',
    'en-NG',
    1,
    TRUE,
    'Your Gideon Heritage Bank staff status has been updated',
    'Staff status updated',
    '<html><body><h2>Hello {{firstName}},</h2><p>Your customer identity has been converted to a staff identity.</p><p>Your employee number is {{employeeNumber}}.</p><p>Your assigned staff role is {{staffRole}}.</p></body></html>',
    'HTML',
    'Notification that a customer identity was converted to staff.'
),

(
    gen_random_uuid(),
    'identity.email-change-verification',
    'EMAIL',
    'en-NG',
    1,
    TRUE,
    'Verify your new Gideon Heritage Bank email address',
    'Confirm your new email address',
    '<html><body><h2>Hello {{firstName}},</h2><p>We received a request to change your registered email address.</p><p><a href="{{verificationUrl}}">Confirm your new email address</a></p><p>This link expires at {{expiresAt}}.</p><p>If you did not request this change, contact the bank immediately.</p></body></html>',
    'HTML',
    'Verification email for a requested email address change.'
),

(
    gen_random_uuid(),
    'identity.email-changed',
    'EMAIL',
    'en-NG',
    1,
    TRUE,
    'Your Gideon Heritage Bank email address has changed',
    'Email address changed',
    '<html><body><h2>Hello {{firstName}},</h2><p>Your registered email address has been changed successfully.</p><p>If you did not make this change, contact the bank immediately.</p></body></html>',
    'HTML',
    'Confirmation email after a successful email address change.'
);