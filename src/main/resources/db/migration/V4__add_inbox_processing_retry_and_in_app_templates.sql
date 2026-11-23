-- ============================================================
-- V4: Inbox processing retries and IN_APP identity templates
-- ============================================================


-- ============================================================
-- 1. INBOX RETRY SCHEDULING
-- ============================================================

ALTER TABLE notification_inbox_events
    ADD COLUMN next_attempt_at TIMESTAMPTZ;

CREATE INDEX idx_notification_inbox_ready
    ON notification_inbox_events (
        status,
        next_attempt_at,
        received_at
    )
    WHERE status = 'RECEIVED';


-- ============================================================
-- 2. IN-APP IDENTITY TEMPLATES
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
    'IN_APP',
    'en-NG',
    1,
    TRUE,
    NULL,
    'Verify your email address',
    'Hello {{firstName}}, complete your registration by opening this link: {{verificationUrl}}. This link expires at {{expiresAt}}.',
    'PLAIN_TEXT',
    'In-app customer email verification notification.'
),

(
    gen_random_uuid(),
    'identity.password-reset',
    'IN_APP',
    'en-NG',
    1,
    TRUE,
    NULL,
    'Password reset requested',
    'Hello {{firstName}}, we received a request to reset your banking password. Use this link to continue: {{resetUrl}}. This link expires at {{expiresAt}}.',
    'PLAIN_TEXT',
    'In-app password reset notification.'
),

(
    gen_random_uuid(),
    'identity.staff-onboarding',
    'IN_APP',
    'en-NG',
    1,
    TRUE,
    NULL,
    'Complete staff onboarding',
    'Welcome {{firstName}}. Complete your staff onboarding using this link: {{onboardingUrl}}. This link expires at {{expiresAt}}.',
    'PLAIN_TEXT',
    'In-app staff onboarding notification.'
),

(
    gen_random_uuid(),
    'identity.customer-converted-to-staff',
    'IN_APP',
    'en-NG',
    1,
    TRUE,
    NULL,
    'Your identity is now a staff identity',
    'Hello {{firstName}}, your customer identity has been converted to a staff identity. Employee number: {{employeeNumber}}. Assigned role: {{staffRole}}.',
    'PLAIN_TEXT',
    'In-app customer-to-staff conversion notification.'
),

(
    gen_random_uuid(),
    'identity.email-change-verification',
    'IN_APP',
    'en-NG',
    1,
    TRUE,
    NULL,
    'Verify your new email address',
    'Hello {{firstName}}, confirm your new email address using this link: {{verificationUrl}}. This link expires at {{expiresAt}}.',
    'PLAIN_TEXT',
    'In-app email change verification notification.'
),

(
    gen_random_uuid(),
    'identity.email-changed',
    'IN_APP',
    'en-NG',
    1,
    TRUE,
    NULL,
    'Email address changed',
    'Hello {{firstName}}, your registered email address has been changed successfully. Your previous email address was {{oldEmail}} and your new email address is {{newEmail}}. If you did not make this change, contact the bank immediately.',
    'PLAIN_TEXT',
    'In-app email change confirmation notification.'
);