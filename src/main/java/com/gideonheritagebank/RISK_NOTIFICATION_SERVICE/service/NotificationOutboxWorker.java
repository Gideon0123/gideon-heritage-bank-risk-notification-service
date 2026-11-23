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

    private static final long LEASE_SAFETY_MARGIN_SECONDS = 30;

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationOutboxPublisher outboxPublisher;
    private final NotificationOutboxService outboxService;
    private final NotificationOutboxRetryService retryService;
    private final NotificationOutboxProperties properties;

    private final String workerId = UUID.randomUUID().toString();

    @Scheduled(
            fixedDelayString = "${notification.outbox.fixed-delay:1000}"
    )
    public void processOutbox() {

        properties.validateWorkerConfiguration();

        Instant now = Instant.now();

        long effectiveLeaseDuration = calculateEffectiveLeaseDuration();

        Instant leaseExpiry = now.plusSeconds(effectiveLeaseDuration);

        List<NotificationOutbox> records = outboxRepository.claimAvailable(
                workerId,
                now,
                leaseExpiry,
                properties.getBatchSize()
        );

        if (records.isEmpty()) {
            return;
        }

        log.debug(
                "Outbox worker claimed records. workerId={}, count={}, leaseSeconds={}",
                workerId,
                records.size(),
                effectiveLeaseDuration
        );

        for (NotificationOutbox outbox : records) {
            processRecord(outbox);
        }
    }

    private void processRecord(NotificationOutbox outbox) {

        try {

            outboxPublisher.publish(outbox);

            /*
             * Only mark PUBLISHED after RabbitMQ confirms publication.
             */
            outboxService.markPublished(
                    outbox.getId(),
                    workerId
            );

            log.info(
                    "Notification delivery request published. "
                            + "outboxId={}, deliveryId={}, messageId={}",
                    outbox.getId(),
                    outbox.getDeliveryId(),
                    outbox.getMessageId()
            );

        } catch (Exception exception) {

            log.error(
                    "Notification outbox publishing failed. "
                            + "outboxId={}, workerId={}",
                    outbox.getId(),
                    workerId,
                    exception
            );

            try {

                retryService.handlePublishingFailure(
                        outbox.getId(),
                        workerId,
                        exception
                );

            } catch (Exception retryException) {

                /*
                 * If retry scheduling fails because the database is
                 * unavailable, the row may remain PROCESSING.
                 *
                 * The independent lease recovery worker will recover it
                 * after locked_until expires.
                 */
                log.error(
                        "Could not schedule outbox retry. "
                                + "Lease recovery will handle the record "
                                + "after expiry. outboxId={}",
                        outbox.getId(),
                        retryException
                );
            }
        }
    }

    private long calculateEffectiveLeaseDuration() {

        /*
         * The worker publishes records sequentially.
         *
         * A batch of 20 records with a 10-second confirmation timeout
         * could take approximately 200 seconds.
         *
         * Add a safety margin so the last records do not expire while
         * the same worker is still processing the batch.
         */

        long estimatedBatchDuration = Math.multiplyExact(
                (long) properties.getBatchSize(),
                properties.getPublisherConfirmTimeoutSeconds()
        );

        long minimumLease = Math.addExact(
                estimatedBatchDuration,
                LEASE_SAFETY_MARGIN_SECONDS
        );

        return Math.max(
                properties.getLeaseDurationSeconds(),
                minimumLease
        );
    }
}