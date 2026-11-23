package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.response;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationOutboxStatus;

import java.time.Instant;
import java.util.Map;

public record NotificationOutboxMonitoringSnapshot(

        Map<NotificationOutboxStatus, Long> statusCounts,
        long totalRecords,
        long pendingCount,
        long processingCount,
        long retryScheduledCount,
        long publishedCount,
        long deadLetteredCount,
        Instant oldestPendingCreatedAt,
        Long oldestPendingAgeSeconds,
        boolean backlogAlert,
        Instant checkedAt

) {
}