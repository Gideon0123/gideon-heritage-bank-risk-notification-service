package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config.NotificationEmailProperties;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationChannel;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationDeliveryStatus;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository.NotificationDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailDeliveryScheduler {

    private final NotificationDeliveryRepository deliveryRepository;
    private final EmailDeliveryExecutionService executionService;
    private final EmailDeliveryStateService stateService;
    private final NotificationEmailProperties properties;


    /*
     * Safety net and retry worker.
     *
     * It picks up:
     * - PENDING email deliveries
     * - RETRY_SCHEDULED email deliveries whose nextAttemptAt is due
     *
     * The database claim operation prevents this scheduler from
     * sending the same delivery concurrently with the RabbitMQ consumer.
     */
    @Scheduled(fixedDelayString = "${notification.email.retry-fixed-delay-millis:5000}")
    public void processDueEmailDeliveries() {

        try {
            properties.validate();

            List<UUID> deliveryIds = deliveryRepository.findDueDeliveryIds(
                    NotificationChannel.EMAIL,
                    List.of(
                            NotificationDeliveryStatus.PENDING,
                            NotificationDeliveryStatus.RETRY_SCHEDULED
                    ),
                    Instant.now(),
                    PageRequest.of(
                            0,
                            properties.getRetryBatchSize()
                    )
            );

            if (deliveryIds.isEmpty()) {
                return;
            }

            log.debug(
                    "Email retry scheduler selected {} deliveries.",
                    deliveryIds.size()
            );

            for (UUID deliveryId : deliveryIds) {

                String workerId = "email-retry-" + UUID.randomUUID();

                try {
                    executionService.process(
                            deliveryId,
                            null,
                            workerId
                    );

                } catch (Exception exception) {

                    log.error(
                            "Scheduled email delivery processing failed. "
                                    + "deliveryId={}",
                            deliveryId,
                            exception
                    );
                }
            }

        } catch (Exception exception) {

            log.error(
                    "Email retry scheduler failed.",
                    exception
            );
        }
    }


    /*
     * Recovers PROCESSING deliveries whose workers crashed,
     * stopped or failed before completing the database update.
     */
    @Scheduled(fixedDelayString = "${notification.email.recovery-fixed-delay-millis:10000}")
    public void recoverExpiredEmailLeases() {

        try {
            Instant now = Instant.now();

            List<UUID> expiredDeliveryIds = deliveryRepository.findExpiredLeaseIds(
                    NotificationChannel.EMAIL,
                    NotificationDeliveryStatus.PROCESSING,
                    now,
                    PageRequest.of(
                            0,
                            properties.getRetryBatchSize()
                    )
            );

            if (expiredDeliveryIds.isEmpty()) {
                return;
            }

            int recovered = 0;

            for (UUID deliveryId : expiredDeliveryIds) {

                try {

                    stateService.recoverExpiredLease(
                            deliveryId,
                            now
                    );

                    recovered++;

                } catch (Exception exception) {

                    log.error(
                            "Could not recover expired email delivery lease. "
                                    + "deliveryId={}",
                            deliveryId,
                            exception
                    );
                }
            }

            log.info(
                    "Email delivery lease recovery completed. "
                            + "selected={}, processed={}",
                    expiredDeliveryIds.size(),
                    recovered
            );

        } catch (Exception exception) {

            log.error(
                    "Email delivery lease recovery scheduler failed.",
                    exception
            );
        }
    }
}