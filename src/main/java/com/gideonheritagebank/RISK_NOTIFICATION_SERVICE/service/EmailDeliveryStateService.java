package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationDelivery;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationDeliveryStatus;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository.NotificationDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailDeliveryStateService {

    private final NotificationDeliveryRepository deliveryRepository;
    private final EmailDeliveryRetryPolicy retryPolicy;


    @Transactional
    public void markSent(
            UUID deliveryId,
            String workerId,
            EmailSendResult result
    ) {
        NotificationDelivery delivery = findOwnedProcessingDelivery(
                deliveryId,
                workerId
        );

        delivery.markSent(
                result.provider(),
                result.providerMessageId()
        );

        log.info(
                "Email accepted by SMTP provider. deliveryId={}, provider={}",
                deliveryId,
                result.provider()
        );
    }

    /*
     * SMTP failures are retryable by default.
     *
     * Once attempts are exhausted, the delivery becomes
     * DEAD_LETTERED and no longer appears in the retry scan.
     */
    @Transactional
    public void scheduleRetry(
            UUID deliveryId,
            String workerId,
            Throwable failure
    ) {
        NotificationDelivery delivery = findOwnedProcessingDelivery(
                deliveryId,
                workerId
        );

        String error = resolveError(failure);

        if (!delivery.hasAttemptsRemaining()) {

            delivery.markDeadLettered(error);

            log.error(
                    "Email delivery exhausted its attempts and was dead-lettered. "
                            + "deliveryId={}, attempts={}",
                    deliveryId,
                    delivery.getAttemptCount()
            );

            return;
        }

        Instant retryAt = retryPolicy.calculateNextAttemptAt(
                delivery.getAttemptCount(),
                Instant.now()
        );

        delivery.scheduleRetry(
                retryAt,
                error
        );

        log.warn(
                "Email delivery retry scheduled. "
                        + "deliveryId={}, attempt={}, retryAt={}",
                deliveryId,
                delivery.getAttemptCount(),
                retryAt
        );
    }

    /*
     * Failures that cannot be fixed by retrying:
     * - invalid or missing destination
     * - missing encrypted content
     * - decryption/authentication failure
     * - invalid email format
     * - malformed MIME message
     */
    @Transactional
    public void markFailed(
            UUID deliveryId,
            String workerId,
            Throwable failure
    ) {

        NotificationDelivery delivery = findOwnedProcessingDelivery(
                deliveryId,
                workerId
        );

        String error = resolveError(failure);
        delivery.markFailed(error);

        log.error(
                "Email delivery permanently failed. deliveryId={}, attempt={}",
                deliveryId,
                delivery.getAttemptCount()
        );
    }


    @Transactional
    public void recoverExpiredLease(
            UUID deliveryId,
            Instant now
    ) {
        NotificationDelivery delivery = deliveryRepository.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new IllegalStateException(
                        "Email delivery not found: " + deliveryId
                ));

        if (delivery.getChannel()
                != com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationChannel.EMAIL) {
            return;
        }

        if (delivery.getStatus()
                != NotificationDeliveryStatus.PROCESSING) {
            return;
        }

        if (delivery.getLockedUntil() == null
                || delivery.getLockedUntil().isAfter(now)) {
            return;
        }

        String error =
                "Email worker lease expired before processing completed.";

        if (!delivery.hasAttemptsRemaining()) {

            delivery.markDeadLettered(
                    error + " Maximum attempts reached."
            );

            log.error(
                    "Expired email delivery lease dead-lettered. "
                            + "deliveryId={}, attempts={}",
                    deliveryId,
                    delivery.getAttemptCount()
            );

            return;
        }

        Instant retryAt = retryPolicy.calculateNextAttemptAt(
                delivery.getAttemptCount(),
                now
        );

        delivery.recoverExpiredLease(
                now,
                retryAt,
                error
        );

        log.warn(
                "Expired email delivery lease recovered. "
                        + "deliveryId={}, retryAt={}",
                deliveryId,
                retryAt
        );
    }


    private NotificationDelivery findOwnedProcessingDelivery(
            UUID deliveryId,
            String workerId
    ) {
        NotificationDelivery delivery = deliveryRepository.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new IllegalStateException(
                        "Email delivery not found: " + deliveryId
                ));

        if (delivery.getStatus() != NotificationDeliveryStatus.PROCESSING) {

            throw new IllegalStateException("Email delivery is no longer PROCESSING.");
        }

        if (workerId == null || !workerId.equals(delivery.getLockedBy())) {

            throw new IllegalStateException("Email worker does not own this delivery.");
        }

        return delivery;
    }


    private String resolveError(Throwable failure) {
        if (failure == null) {
            return "Unknown email delivery failure.";
        }
        if (failure instanceof org.springframework.mail.MailException) {
            return "SMTP provider failure (" + failure.getClass().getSimpleName() + ").";
        }
        return "Email delivery failure (" + failure.getClass().getSimpleName() + ").";
    }
}