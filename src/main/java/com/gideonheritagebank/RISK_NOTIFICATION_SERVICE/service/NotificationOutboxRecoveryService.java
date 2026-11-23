package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config.NotificationOutboxProperties;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationOutbox;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
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

    /*
     * The query uses FOR UPDATE SKIP LOCKED.
     *
     * The row locks remain held while the recovery operations execute
     * because this entire method is transactional.
     */
    @Transactional
    public int recoverExpiredLeases() {

        Instant now = Instant.now();

        List<NotificationOutbox> expiredRecords =
                outboxRepository.findExpiredLeasesForRecovery(
                        now,
                        properties.getRecoveryBatchSize()
                );

        if (expiredRecords.isEmpty()) {
            return 0;
        }

        int recovered = 0;
        int deadLettered = 0;

        for (NotificationOutbox outbox : expiredRecords) {

            boolean attemptsRemain = outbox.hasAttemptsRemaining();

            retryService.recoverExpiredRecord(
                    outbox,
                    now
            );

            if (attemptsRemain) {
                recovered++;
            } else {
                deadLettered++;
            }
        }

        log.info(
                "Outbox lease recovery completed. "
                        + "selected={}, recovered={}, deadLettered={}",
                expiredRecords.size(),
                recovered,
                deadLettered
        );

        return expiredRecords.size();
    }

    @Scheduled(
            fixedDelayString =
                    "${notification.outbox.recovery-fixed-delay:5000}"
    )
    public void runRecovery() {

        try {

            int processed = recoverExpiredLeases();

            if (processed > 0) {
                log.info(
                        "Outbox recovery worker processed {} expired records.",
                        processed
                );
            }

        } catch (Exception exception) {

            log.error(
                    "Notification outbox recovery execution failed.",
                    exception
            );
        }
    }
}