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
    private final NotificationOutboxProperties properties;
    private final String workerId = UUID.randomUUID().toString();

    @Scheduled(
            fixedDelayString = "${notification.outbox.fixed-delay:1000}"
    )
    public void processOutbox() {

        Instant now = Instant.now();

        Instant leaseExpiry = now.plusSeconds(
                properties.getLeaseDurationSeconds()
        );

        List<NotificationOutbox> records =
                outboxRepository.claimAvailable(
                        workerId,
                        now,
                        leaseExpiry,
                        properties.getBatchSize()
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

            /*
             * Publishes to RabbitMQ and waits for the publisher
             * confirmation.
             */
            outboxPublisher.publish(outbox);

            /*
             * Mark as published only after a successful broker ACK.
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
                 * If retry scheduling fails, the record remains
                 * PROCESSING. The lease recovery service will
                 * eventually recover it.
                 */
                log.error(
                        "Could not schedule outbox retry. "
                                + "The lease recovery worker will handle it. "
                                + "outboxId={}",
                        outbox.getId(),
                        retryException
                );
            }
        }
    }
}