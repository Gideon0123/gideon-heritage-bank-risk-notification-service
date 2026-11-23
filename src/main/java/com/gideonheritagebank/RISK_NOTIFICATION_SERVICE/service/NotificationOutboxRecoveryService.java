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
    private final NotificationOutboxProperties outboxProperties;

    /**
     * Periodically recovers outbox records whose worker leases
     * have expired.
     */
    @Scheduled(
            fixedDelayString = "${notification.outbox.recovery.fixed-delay:30000}"
    )
    @Transactional
    public void recoverExpiredLeases() {

        Instant now = Instant.now();

        int batchSize = outboxProperties
                .getRecovery()
                .getBatchSize();

        if (batchSize < 1) {

            throw new IllegalStateException(
                    "Outbox recovery batch size must be at least 1."
            );
        }

        List<NotificationOutbox> expiredRecords =
                outboxRepository.findExpiredLeasesForRecovery(
                        now,
                        batchSize
                );

        if (expiredRecords.isEmpty()) {
            return;
        }

        int recovered = 0;

        int deadLettered = 0;

        for (NotificationOutbox outbox : expiredRecords) {

            try {
                boolean attemptsRemain = outbox.hasAttemptsRemaining();

                retryService.recoverExpiredRecord(outbox);

                if (attemptsRemain) {
                    recovered++;
                } else {
                    deadLettered++;
                }

            } catch (RuntimeException exception) {

                /*
                 * Do not swallow database consistency failures
                 * and continue committing a partially processed batch.
                 *
                 * Propagating the exception causes this transaction
                 * to roll back.
                 */
                log.error(
                        "Failed to recover expired outbox record. outboxId={}",
                        outbox.getId(),
                        exception
                );

                throw exception;
            }
        }

        log.warn(
                "Outbox lease recovery completed. recovered={}, deadLettered={}, batchSize={}",
                recovered,
                deadLettered,
                expiredRecords.size()
        );
    }
}