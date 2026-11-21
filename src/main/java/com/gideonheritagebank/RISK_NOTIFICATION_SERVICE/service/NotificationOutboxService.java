package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config.NotificationOutboxProperties;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.event.NotificationDeliveryRequestedEvent;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.Notification;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationDelivery;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationOutbox;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository.NotificationOutboxRepository;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository.NotificationRepository;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository.NotificationDeliveryRepository;
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
    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationOutboxProperties outboxProperties;
    private final ObjectMapper objectMapper;

    /**
     * Persists a notification, all its delivery records,
     * and the corresponding outbox records in one transaction.
     * The caller must prepare the notification and attach
     * its delivery records using notification.addDelivery().
     * If any operation fails, the complete transaction rolls back.
     */
    @Transactional
    public UUID createNotificationWithDeliveries(
            Notification notification
    ) {
        validateNotification(notification);

        /*
         * Save the notification.
         * CascadeType.ALL on Notification.deliveries ensures
         * its delivery records are persisted as well.
         */
        Notification savedNotification = notificationRepository.saveAndFlush(notification);

        List<NotificationOutbox> outboxRecords = new ArrayList<>();

        /*
         * At this point, Hibernate has flushed the notification
         * and delivery records, so their generated UUIDs are available.
         */
        for (NotificationDelivery delivery : savedNotification.getDeliveries()) {

            if (delivery.getId() == null) {
                throw new IllegalStateException(
                        "Notification delivery ID was not generated."
                );
            }

            String routingKey = outboxProperties.routingKeyFor(
                    delivery.getChannel().name()
            );

            NotificationDeliveryRequestedEvent event = NotificationDeliveryRequestedEvent.create(
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
         * All outbox records are persisted in the same transaction
         * as the notification and delivery records.
         */
        outboxRepository.saveAllAndFlush(outboxRecords);

        log.info(
                "Notification and {} outbox records created. notificationId={}",
                outboxRecords.size(),
                savedNotification.getId()
        );

        return savedNotification.getId();
    }


    /**
     * Marks an outbox record as successfully published.
     * We verify worker ownership so an old worker cannot
     * update a record that another worker has reclaimed.
     */
    @Transactional
    public void markPublished(
            UUID outboxId,
            String workerId
    ) {
        NotificationOutbox outbox = outboxRepository.findById(outboxId)
                .orElseThrow(() -> new IllegalStateException(
                        "Notification outbox record not found: " + outboxId
                ));

        if (outbox.getLockedBy() == null || !outbox.getLockedBy().equals(workerId)) {

            throw new IllegalStateException(
                    "Worker does not own this outbox record."
            );
        }

        outbox.markPublished();
    }


    private void validateNotification(Notification notification) {

        if (notification == null) {
            throw new IllegalArgumentException("Notification is required.");
        }

        if (notification.getDeliveries() == null || notification.getDeliveries().isEmpty()) {

            throw new IllegalArgumentException("At least one notification delivery is required.");
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
