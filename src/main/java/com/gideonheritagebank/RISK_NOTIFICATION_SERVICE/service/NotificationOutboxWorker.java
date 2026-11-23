package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config.NotificationOutboxProperties;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationOutbox;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationOutboxWorker {

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationOutboxPublisher publisher;
    private final NotificationOutboxService outboxService;
    private final NotificationOutboxProperties outboxProperties;

    /*
     * Each running application instance has its own worker ID.
     * This allows us to identify which worker owns a lease
     * and prevents another worker from completing that record.
     */
    private final String workerId = UUID.randomUUID().toString();


    /**
     * Periodically claims and publishes available outbox records.
     * The repository claim operation has its own transaction.
     * It commits before this method begins publishing messages.
     */
    @Scheduled(
            fixedDelayString = "${notification.outbox.fixed-delay:1000}"
    )
    public void processOutbox() {

        Instant now = Instant.now();
        Instant leaseExpiry = now.plusSeconds(outboxProperties.getLeaseDurationSeconds());

        List<NotificationOutbox> records;

        try {

            records = outboxRepository.claimAvailable(
                    workerId,
                    now,
                    leaseExpiry,
                    outboxProperties.getBatchSize()
            );

        } catch (Exception exception) {

            log.error(
                    "Unable to claim notification outbox records.",
                    exception
            );

            return;
        }

        if (records.isEmpty()) {
            return;
        }

        log.debug(
                "Worker claimed {} notification outbox records. workerId={}",
                records.size(),
                workerId
        );

        for (NotificationOutbox outbox : records) {

            processRecord(outbox);
        }
    }


    private void processRecord(NotificationOutbox outbox) {

        try {

            /* Publish and wait for RabbitMQ confirmation. */
            publisher.publish(outbox);

            /*
             * Only after successful confirmation do we
             * mark the outbox record as published.
             */
            outboxService.markPublished(
                    outbox.getId(),
                    workerId
            );

        } catch (Exception exception) {

            /*
             * We intentionally do not mark the record as
             * published or failed here.
             * Part B will introduce the retry service, which
             * will transition failed publishing attempts into
             * RETRY_SCHEDULED or DEAD_LETTERED.
             * Until then, the lease will expire and the
             * recovery mechanism will reclaim the record.
             */
            log.error(
                    "Failed to publish notification outbox record. "
                            + "outboxId={}, messageId={}",
                    outbox.getId(),
                    outbox.getMessageId(),
                    exception
            );
        }
    }
}
