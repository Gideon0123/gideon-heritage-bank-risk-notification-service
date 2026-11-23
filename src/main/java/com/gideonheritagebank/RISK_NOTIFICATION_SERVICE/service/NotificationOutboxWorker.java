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
    private final NotificationOutboxPublisher outboxPublisher;
    private final NotificationOutboxService outboxService;
    private final NotificationOutboxRetryService retryService;
    private final NotificationOutboxProperties outboxProperties;
    private final String workerId = UUID.randomUUID().toString();

    @Scheduled(
            fixedDelayString = "${notification.outbox.fixed-delay:1000}"
    )
    public void processOutbox() {

        Instant now = Instant.now();

        Instant leaseExpiry = now.plusSeconds(
                outboxProperties.getLeaseDurationSeconds()
        );

        List<NotificationOutbox> records = outboxRepository.claimAvailable(
                workerId,
                now,
                leaseExpiry,
                outboxProperties.getBatchSize()
        );

        if (records.isEmpty()) {
            return;
        }

        log.debug(
                "Outbox worker claimed {} records. workerId={}",
                records.size(),
                workerId
        );

        for (NotificationOutbox outbox : records) {

            processRecord(outbox);
        }
    }

    private void processRecord(NotificationOutbox outbox) {

        try {

            /* Publish to RabbitMQ and wait for publisher confirmation. */
            outboxPublisher.publish(outbox);

            /*
             * Only mark published after successful broker confirmation.
             * This method verifies that the worker still owns the record.
             */
            outboxService.markPublished(
                    outbox.getId(),
                    workerId
            );

            log.info(
                    "Notification outbox record published. outboxId={}, messageId={}",
                    outbox.getId(),
                    outbox.getMessageId()
            );

        } catch (Exception exception) {

            log.error(
                    "Notification outbox publishing failed. outboxId={}, attempt={}",
                    outbox.getId(),
                    outbox.getAttemptCount(),
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
                 * This could indicate that the lease was reclaimed,
                 * the worker no longer owns the record, or a database
                 * update failed.
                 *
                 * Never blindly overwrite the record in this situation.
                 */
                log.error(
                        "Could not update failed outbox record. outboxId={}, workerId={}",
                        outbox.getId(),
                        workerId,
                        retryException
                );
            }
        }
    }
}