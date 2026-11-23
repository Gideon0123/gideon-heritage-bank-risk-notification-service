package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.InboxEvent;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.IdentityEventType;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.InboxEventStatus;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.messaging.contract.*;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository.InboxEventRepository;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository.NotificationRepository;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.security.InboxPayloadEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InboxEventProcessingTransaction {

    private final InboxEventRepository inboxEventRepository;
    private final NotificationRepository notificationRepository;

    private final InboxPayloadEncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    private final IdentityEventNotificationFactory notificationFactory;

    /*
     * This transaction includes:
     *
     * 1. InboxEvent -> PROCESSING
     * 2. Decryption and deserialization
     * 3. Notification creation
     * 4. NotificationDelivery creation
     * 5. NotificationOutbox creation
     * 6. InboxEvent -> PROCESSED
     *
     * NotificationOutboxService uses REQUIRED propagation by default,
     * so its transaction joins this transaction.
     *
     * Any failure rolls back all of these operations.
     */
    @Transactional
    public void process(UUID inboxEventId) {

        InboxEvent inboxEvent = inboxEventRepository
                .findByIdForUpdate(inboxEventId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Inbox event not found: " + inboxEventId
                ));

        /*
         * Another application instance may have processed this event
         * after the scheduler selected its ID.
         */
        if (inboxEvent.getStatus() != InboxEventStatus.RECEIVED) {
            return;
        }

        Instant now = Instant.now();

        if (inboxEvent.getNextAttemptAt() != null
                && inboxEvent.getNextAttemptAt().isAfter(now)) {
            return;
        }

        inboxEvent.markProcessing();

        String payloadJson = encryptionService.decrypt(
                inboxEvent.getEncryptedPayload()
        );

        IdentityEventPayload payload = deserialize(
                inboxEvent.getEventType(),
                payloadJson
        );

        validatePayloadAgainstInbox(
                inboxEvent,
                payload
        );

        /*
         * Extra idempotency protection.
         *
         * Normally, inbox locking and atomic transactions are enough.
         * This also protects against a notification created by an
         * earlier implementation or a manual reprocessing operation.
         */
        boolean notificationAlreadyExists =
                notificationRepository
                        .existsByUserIdAndSourceEventTypeAndSourceMessageId(
                                inboxEvent.getUserId(),
                                inboxEvent.getEventType(),
                                inboxEvent.getMessageId()
                        );

        if (!notificationAlreadyExists) {

            notificationFactory.createNotification(
                    inboxEvent,
                    payload
            );
        }

        /*
         * This is committed together with the notification and outbox.
         */
        inboxEvent.markProcessed();

        log.info(
                "Identity inbox event processed. "
                        + "inboxEventId={}, eventType={}, messageId={}",
                inboxEvent.getId(),
                inboxEvent.getEventType(),
                inboxEvent.getMessageId()
        );
    }

    private IdentityEventPayload deserialize(
            String eventType,
            String payloadJson
    ) {

        IdentityEventType identityEventType;

        try {

            identityEventType = IdentityEventType.valueOf(eventType);

        } catch (IllegalArgumentException exception) {

            throw new IllegalArgumentException(
                    "Unsupported Identity event type: " + eventType,
                    exception
            );
        }

        try {

            return switch (identityEventType) {

                case CUSTOMER_EMAIL_VERIFICATION_REQUESTED ->
                        objectMapper.readValue(
                                payloadJson,
                                EmailVerificationMessage.class
                        );

                case PASSWORD_RESET_REQUESTED ->
                        objectMapper.readValue(
                                payloadJson,
                                PasswordResetMessage.class
                        );

                case STAFF_ONBOARDING_REQUESTED ->
                        objectMapper.readValue(
                                payloadJson,
                                StaffOnboardingMessage.class
                        );

                case CUSTOMER_CONVERTED_TO_STAFF ->
                        objectMapper.readValue(
                                payloadJson,
                                CustomerConvertedToStaffMessage.class
                        );

                case EMAIL_CHANGE_VERIFICATION_REQUESTED ->
                        objectMapper.readValue(
                                payloadJson,
                                EmailChangeVerificationMessage.class
                        );

                case EMAIL_CHANGE_CONFIRMED ->
                        objectMapper.readValue(
                                payloadJson,
                                EmailChangedConfirmedMessage.class
                        );
            };

        } catch (JsonProcessingException exception) {

            throw new IllegalStateException(
                    "Could not deserialize the stored Identity event.",
                    exception
            );
        }
    }

    private void validatePayloadAgainstInbox(
            InboxEvent inboxEvent,
            IdentityEventPayload payload
    ) {

        if (payload == null) {
            throw new IllegalStateException(
                    "Stored Identity event payload is empty."
            );
        }

        if (!Objects.equals(
                inboxEvent.getEventType(),
                payload.eventType()
        )) {

            throw new IllegalStateException(
                    "Stored event type does not match the payload event type."
            );
        }

        if (!Objects.equals(
                inboxEvent.getUserId(),
                payload.userId()
        )) {

            throw new IllegalStateException(
                    "Stored event user ID does not match the payload user ID."
            );
        }
    }
}