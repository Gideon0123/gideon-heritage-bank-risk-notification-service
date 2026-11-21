package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.messaging.contract;

import java.time.Instant;
import java.util.UUID;

public record EmailChangeVerificationMessage(
        String eventType,
        UUID userId,
        String oldEmail,
        String newEmail,
        String firstName,
        String verificationToken,
        String verificationUrl,
        Instant expiresAt
) implements IdentityEventPayload {
}