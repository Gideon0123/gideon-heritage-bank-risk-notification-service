package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config.NotificationOutboxProperties;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.event.NotificationDeliveryRequestedEvent;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.Notification;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationDelivery;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationOutbox;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationOutboxStatus;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository.NotificationOutboxRepository;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationOutboxService {

    private static final String DELIVERY_EVENT_TYPE = "NOTIFICATION_DELIVERY_REQUESTED";

    private final NotificationRepository notificationRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationOutboxProperties outboxProperties;
    private final ObjectMapper objectMapper;

    /**
     * Persists:
     * 1. Notification
     * 2. Its NotificationDelivery records
     * 3. Corresponding NotificationOutbox records
     * All records are committed in one database transaction.
     * If no delivery channels are enabled or available, the
     * Notification is still persisted, but no outbox record
     * is created.
     */
    @Transactional
    public UUID createNotificationWithDeliveries(
            Notification notification
    ) {

        validateNotification(notification);

        /*
         * Notification.deliveries uses CascadeType.ALL.
         * Attached delivery records are therefore persisted
         * together with the notification.
         */
        Notification savedNotification = notificationRepository.saveAndFlush(notification);

        List<NotificationDelivery> deliveries = savedNotification.getDeliveries();

        if (deliveries == null || deliveries.isEmpty()) {

            log.info(
                    "Notification created without outbound deliveries. "
                            + "notificationId={}",
                    savedNotification.getId()
            );

            return savedNotification.getId();
        }

        List<NotificationOutbox> outboxRecords = new ArrayList<>();

        for (NotificationDelivery delivery : deliveries) {

            if (delivery.getId() == null) {
                throw new IllegalStateException(
                        "Notification delivery ID was not generated."
                );
            }

            String routingKey = outboxProperties.routingKeyFor(
                    delivery.getChannel().name()
            );

            NotificationDeliveryRequestedEvent event =
                    NotificationDeliveryRequestedEvent.create(
                            delivery.getId(),
                            savedNotification.getId(),
                            delivery.getChannel()
                    );

            String payload = serialize(event);

            NotificationOutbox outbox = NotificationOutbox.create(
                    delivery,
                    DELIVERY_EVENT_TYPE,
                    outboxProperties.getExchange(),
                    routingKey,
                    payload,
                    outboxProperties.getMaxAttempts()
            );

            outboxRecords.add(outbox);
        }

        /*
         * If saving any outbox record fails, the notification
         * and all its deliveries also roll back.
         */
        outboxRepository.saveAllAndFlush(outboxRecords);

        log.info(
                "Notification, deliveries and outbox records created. "
                        + "notificationId={}, deliveryCount={}, outboxCount={}",
                savedNotification.getId(),
                deliveries.size(),
                outboxRecords.size()
        );

        return savedNotification.getId();
    }

    /**
     * Only the worker that currently owns the PROCESSING lease
     * may mark a record as published.
     */
    @Transactional
    public void markPublished(
            UUID outboxId,
            String workerId
    ) {

        NotificationOutbox outbox = outboxRepository
                .findByIdForUpdate(outboxId)
                .orElseThrow(() -> new IllegalStateException(
                        "Notification outbox record not found: " + outboxId
                ));

        if (outbox.getStatus() != NotificationOutboxStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Only PROCESSING outbox records can be marked as published."
            );
        }

        if (workerId == null
                || !workerId.equals(outbox.getLockedBy())) {

            throw new IllegalStateException(
                    "Worker does not own this outbox record."
            );
        }

        outbox.markPublished();
    }

    private void validateNotification(Notification notification) {

        if (notification == null) {
            throw new IllegalArgumentException(
                    "Notification is required."
            );
        }
    }

    private String serialize(
            NotificationDeliveryRequestedEvent event
    ) {

        try {

            return objectMapper.writeValueAsString(event);

        } catch (JsonProcessingException exception) {

            throw new IllegalStateException(
                    "Unable to serialize notification delivery event.",
                    exception
            );
        }
    }
}
