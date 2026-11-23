package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config.NotificationOutboxProperties;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationOutbox;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationOutboxRecoveryService {

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationOutboxRetryService retryService;
    private final NotificationOutboxProperties properties;

    @Transactional
    public int recoverExpiredLeases() {

        Instant now = Instant.now();

        List<NotificationOutbox> expiredRecords = outboxRepository.findExpiredLeasesForRecovery(
                now,
                properties.getRecoveryBatchSize()
        );

        if (expiredRecords.isEmpty()) {
            return 0;
        }

        for (NotificationOutbox outbox : expiredRecords) {

            retryService.recoverExpiredRecord(
                    outbox,
                    now
            );
        }

        log.info(
                "Expired outbox lease recovery completed. processed={}",
                expiredRecords.size()
        );

        return expiredRecords.size();
    }
}