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
     * The entire recovery batch is one transaction.
     *
     * The custom repository query uses FOR UPDATE SKIP LOCKED.
     * The locks remain held until this method's transaction commits.
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

        for (NotificationOutbox outbox : expiredRecords) {

            try {

                retryService.recoverExpiredRecord(
                        outbox,
                        now
                );

                recovered++;

            } catch (Exception exception) {

                log.error(
                        "Failed to recover expired outbox lease. outboxId={}",
                        outbox.getId(),
                        exception
                );

                throw new IllegalStateException(
                        "Outbox lease recovery failed.",
                        exception
                );
            }
        }

        log.info(
                "Expired outbox lease recovery completed. "
                        + "selected={}, processed={}",
                expiredRecords.size(),
                recovered
        );

        return recovered;
    }


    /* Runs independently of the normal publisher worker. */
    @Scheduled(
            fixedDelayString = "${notification.outbox.recovery-fixed-delay:5000}"
    )
    public void runRecovery() {

        try {

            int recovered = recoverExpiredLeases();

            if (recovered > 0) {

                log.info(
                        "Outbox recovery worker processed {} expired records.",
                        recovered
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