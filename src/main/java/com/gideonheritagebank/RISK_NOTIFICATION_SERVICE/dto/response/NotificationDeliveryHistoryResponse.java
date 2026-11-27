package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.response;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationDelivery;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationChannel;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationDeliveryStatus;
import java.time.Instant;
import java.util.UUID;

public record NotificationDeliveryHistoryResponse(
        UUID id, UUID notificationId, NotificationChannel channel,
        NotificationDeliveryStatus status, String provider, String providerMessageId,
        int attemptCount, int maxAttempts, Instant nextAttemptAt, Instant lastAttemptAt,
        Instant sentAt, Instant deliveredAt, Instant failedAt,
        Instant createdAt, Instant updatedAt
) {
    public static NotificationDeliveryHistoryResponse from(NotificationDelivery d) {
        return new NotificationDeliveryHistoryResponse(d.getId(), d.getNotification().getId(),
                d.getChannel(), d.getStatus(), d.getProvider(), d.getProviderMessageId(),
                d.getAttemptCount(), d.getMaxAttempts(), d.getNextAttemptAt(), d.getLastAttemptAt(),
                d.getSentAt(), d.getDeliveredAt(), d.getFailedAt(),
                d.getCreatedAt(), d.getUpdatedAt());
    }
}
