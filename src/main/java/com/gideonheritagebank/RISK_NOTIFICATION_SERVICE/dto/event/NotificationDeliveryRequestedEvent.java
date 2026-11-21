package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.event;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationChannel;

import java.time.Instant;
import java.util.UUID;

public record NotificationDeliveryRequestedEvent(

        int schemaVersion,
        String eventType,
        UUID deliveryId,
        UUID notificationId,
        NotificationChannel channel,
        Instant occurredAt

) {

    public static NotificationDeliveryRequestedEvent create(
            UUID deliveryId,
            UUID notificationId,
            NotificationChannel channel
    ) {
        return new NotificationDeliveryRequestedEvent(
                1,
                "NOTIFICATION_DELIVERY_REQUESTED",
                deliveryId,
                notificationId,
                channel,
                Instant.now()
        );
    }
}
