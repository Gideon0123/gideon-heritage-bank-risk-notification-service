package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationOutbox;

import java.time.Instant;
import java.util.List;

public interface NotificationOutboxRepositoryCustom {

    List<NotificationOutbox> claimAvailable(
            String workerId,
            Instant now,
            Instant leaseExpiry,
            int batchSize
    );
}