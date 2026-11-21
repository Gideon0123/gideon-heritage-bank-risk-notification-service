package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.messaging.contract;

import java.time.Instant;
import java.util.UUID;

public record StaffOnboardingMessage(
        String eventType,
        UUID userId,
        String email,
        String firstName,
        String onboardingToken,
        String onboardingUrl,
        Instant expiresAt
) implements IdentityEventPayload {
}