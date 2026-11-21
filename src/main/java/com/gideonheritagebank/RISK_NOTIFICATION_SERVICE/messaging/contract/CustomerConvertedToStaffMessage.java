package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.messaging.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CustomerConvertedToStaffMessage(
        String eventType,
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String employeeNumber,
        String staffRole,
        Instant convertedAt,
        String deliveryPolicy,
        List<String> availableChannels
) implements IdentityEventPayload {
}
