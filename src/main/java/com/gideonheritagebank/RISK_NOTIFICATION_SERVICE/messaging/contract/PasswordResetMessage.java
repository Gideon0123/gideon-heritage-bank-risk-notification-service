package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.messaging.contract;

import java.time.Instant;
import java.util.UUID;

public record PasswordResetMessage(
        String eventType,
        UUID userId,
        String email,
        String firstName,
        String resetToken,
        String resetUrl,
        Instant expiresAt
) implements IdentityEventPayload {
}