package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.response;

import java.time.Instant;

public record NotificationOutboxMonitoringResponse(

        long totalRecords,
        long pending,
        long processing,
        long retryScheduled,
        long published,
        long deadLettered,
        Instant oldestPendingCreatedAt,
        Instant oldestRetryCreatedAt,
        Instant oldestProcessingCreatedAt,
        Instant oldestDeadLetterCreatedAt,
        Instant generatedAt

) {
}