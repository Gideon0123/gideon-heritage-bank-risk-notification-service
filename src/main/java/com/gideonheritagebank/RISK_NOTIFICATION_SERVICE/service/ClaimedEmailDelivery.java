package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationContentFormat;

import java.util.UUID;

public record ClaimedEmailDelivery(

        UUID deliveryId,
        UUID notificationId,
        String workerId,
        String encryptedDestination,
        String subject,
        String encryptedContent,
        NotificationContentFormat contentFormat,
        int attemptNumber

) {
}