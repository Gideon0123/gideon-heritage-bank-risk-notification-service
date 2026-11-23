package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config.NotificationEmailProperties;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationDelivery;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationChannel;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationDeliveryStatus;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository.NotificationDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailDeliveryClaimService {

    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationEmailProperties properties;

    @Transactional
    public Optional<ClaimedEmailDelivery> claim(
            UUID deliveryId,
            UUID expectedNotificationId,
            String workerId
    ) {

        if (deliveryId == null) {
            throw new IllegalArgumentException(
                    "Delivery ID is required."
            );
        }

        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException(
                    "Email worker ID is required."
            );
        }

        NotificationDelivery delivery = deliveryRepository
                .findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new IllegalStateException(
                        "Email delivery not found: " + deliveryId
                ));

        if (delivery.getChannel() != NotificationChannel.EMAIL) {
            throw new IllegalStateException(
                    "The requested delivery is not an EMAIL delivery."
            );
        }

        if (expectedNotificationId != null
                && !expectedNotificationId.equals(
                delivery.getNotification().getId()
        )) {

            throw new IllegalStateException(
                    "Delivery does not belong to the notification in the event."
            );
        }

        /*
         * A duplicate RabbitMQ message may arrive after the
         * email has already been sent or permanently completed.
         */
        if (delivery.getStatus() == NotificationDeliveryStatus.SENT
                || delivery.getStatus() == NotificationDeliveryStatus.DELIVERED
                || delivery.getStatus() == NotificationDeliveryStatus.FAILED
                || delivery.getStatus() == NotificationDeliveryStatus.DEAD_LETTERED
                || delivery.getStatus() == NotificationDeliveryStatus.CANCELLED) {

            return Optional.empty();
        }

        /*
         * Another worker currently owns the delivery.
         * Do not send a second copy concurrently.
         */
        if (delivery.getStatus() == NotificationDeliveryStatus.PROCESSING) {
            return Optional.empty();
        }

        if (delivery.getStatus() != NotificationDeliveryStatus.PENDING
                && delivery.getStatus() != NotificationDeliveryStatus.RETRY_SCHEDULED) {

            return Optional.empty();
        }

        Instant now = Instant.now();

        if (delivery.getNextAttemptAt() != null && delivery.getNextAttemptAt().isAfter(now)) {

            return Optional.empty();
        }

        if (!delivery.hasAttemptsRemaining()) {

            delivery.markDeadLettered(
                    "Maximum email delivery attempts have been reached."
            );

            return Optional.empty();
        }

        properties.validate();

        Instant leaseExpiry = now.plusSeconds(properties.getLeaseDurationSeconds());

        delivery.markProcessing(
                workerId,
                leaseExpiry
        );

        return Optional.of(
                new ClaimedEmailDelivery(
                        delivery.getId(),
                        delivery.getNotification().getId(),
                        workerId,
                        delivery.getEncryptedDestination(),
                        delivery.getSubject(),
                        delivery.getEncryptedContent(),
                        delivery.getContentFormat(),
                        delivery.getAttemptCount()
                )
        );
    }
}