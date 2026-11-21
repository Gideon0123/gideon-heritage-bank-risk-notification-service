package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.messaging.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EmailChangedConfirmedMessage(
        String eventType,
        UUID userId,
        String oldEmail,
        String newEmail,
        String firstName,
        String lastName,
        Instant emailChangedAt,
        Instant nextEmailChangeAllowedAt,
        String deliveryPolicy,
        List<String> availableChannels
) implements IdentityEventPayload {
}