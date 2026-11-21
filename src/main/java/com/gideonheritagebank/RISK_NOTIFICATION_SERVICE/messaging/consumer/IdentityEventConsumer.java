package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.messaging.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.IdentityEventType;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.messaging.contract.CustomerConvertedToStaffMessage;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.messaging.contract.EmailChangeVerificationMessage;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.messaging.contract.EmailChangedConfirmedMessage;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.messaging.contract.EmailVerificationMessage;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.messaging.contract.IdentityEventPayload;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.messaging.contract.PasswordResetMessage;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.messaging.contract.StaffOnboardingMessage;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service.InboxEventService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdentityEventConsumer {

    private final ObjectMapper objectMapper;
    private final InboxEventService inboxEventService;

    // CUSTOMER EMAIL VERIFICATION
    @RabbitListener(
            queues = "${notification.messaging.email-verification-queue:identity.email-verification}",
            containerFactory = "identityEventListenerContainerFactory"
    )
    public void consumeEmailVerification(
            Message message,
            Channel channel
    ) throws IOException {

        consume(
                message,
                channel,
                IdentityEventType.CUSTOMER_EMAIL_VERIFICATION_REQUESTED,
                EmailVerificationMessage.class
        );
    }

    // PASSWORD RESET
    @RabbitListener(
            queues = "${notification.messaging.password-reset-queue:identity.password-reset}",
            containerFactory = "identityEventListenerContainerFactory"
    )
    public void consumePasswordReset(
            Message message,
            Channel channel
    ) throws IOException {

        consume(
                message,
                channel,
                IdentityEventType.PASSWORD_RESET_REQUESTED,
                PasswordResetMessage.class
        );
    }

    // STAFF ONBOARDING
    @RabbitListener(
            queues = "${notification.messaging.staff-onboarding-queue:identity.staff-onboarding}",
            containerFactory = "identityEventListenerContainerFactory"
    )
    public void consumeStaffOnboarding(
            Message message,
            Channel channel
    ) throws IOException {

        consume(
                message,
                channel,
                IdentityEventType.STAFF_ONBOARDING_REQUESTED,
                StaffOnboardingMessage.class
        );
    }

    // CUSTOMER CONVERTED TO STAFF
    @RabbitListener(
            queues = "${notification.messaging.customer-converted-queue:identity.customer-converted-to-staff}",
            containerFactory = "identityEventListenerContainerFactory"
    )
    public void consumeCustomerConvertedToStaff(
            Message message,
            Channel channel
    ) throws IOException {

        consume(
                message,
                channel,
                IdentityEventType.CUSTOMER_CONVERTED_TO_STAFF,
                CustomerConvertedToStaffMessage.class
        );
    }

    // EMAIL CHANGE VERIFICATION
    @RabbitListener(
            queues = "${notification.messaging.email-change-verification-queue:identity.email-change-verification}",
            containerFactory = "identityEventListenerContainerFactory"
    )
    public void consumeEmailChangeVerification(
            Message message,
            Channel channel
    ) throws IOException {

        consume(
                message,
                channel,
                IdentityEventType.EMAIL_CHANGE_VERIFICATION_REQUESTED,
                EmailChangeVerificationMessage.class
        );
    }

    // EMAIL CHANGE CONFIRMATION
    @RabbitListener(
            queues = "${notification.messaging.email-changed-queue:identity.email-changed}",
            containerFactory = "identityEventListenerContainerFactory"
    )
    public void consumeEmailChangedConfirmed(
            Message message,
            Channel channel
    ) throws IOException {

        consume(
                message,
                channel,
                IdentityEventType.EMAIL_CHANGE_CONFIRMED,
                EmailChangedConfirmedMessage.class
        );
    }

    // COMMON CONSUMER
    private <T extends IdentityEventPayload> void consume(
            Message message,
            Channel channel,
            IdentityEventType expectedEventType,
            Class<T> payloadClass
    ) throws IOException {

        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String messageId = message.getMessageProperties().getMessageId();

        Object eventTypeHeader = message.getMessageProperties()
                .getHeaders()
                .get("eventType");

        if (messageId == null || messageId.isBlank() || messageId.length() > 255) {

            throw invalidMessage(
                    "Identity event message ID is missing or invalid."
            );
        }

        /*
         * The event type in the JSON payload is the authoritative
         * contract field. Older messages may not have the AMQP header.
         * If the header exists, however, it must agree with the payload
         * and with the queue on which the message was received.
         */

        String contentType = message.getMessageProperties().getContentType();

        if (!contentType.toLowerCase(java.util.Locale.ROOT).contains("json")) {

            throw invalidMessage(
                    "Identity event content type must be JSON."
            );
        }

        T payload;

        try {

            payload = objectMapper.readValue(
                    message.getBody(),
                    payloadClass
            );

        } catch (JsonProcessingException exception) {

            throw invalidMessage(
                    "Identity event contains invalid JSON."
            );
        }

        validatePayload(
                payload,
                expectedEventType
        );

        if (eventTypeHeader == null) {

            /*
             * Backward compatibility for messages published before
             * the eventType AMQP header was consistently added.
             * The payload eventType has already been validated against
             * this listener's expected event type.
             */
            log.warn(
                    "Identity event is missing its AMQP eventType header; validated payload eventType instead: eventType={}, messageId={}",
                    expectedEventType,
                    messageId
            );

        } else if (!expectedEventType.name().equals(eventTypeHeader.toString())) {

            throw invalidMessage(
                    "Identity event type header does not match its destination queue."
            );
        }

        String payloadJson;

        try {
            payloadJson = objectMapper.writeValueAsString(payload);

        } catch (JsonProcessingException exception) {

            /*
             * This is a local serialization failure, not a malformed
             * incoming message. Let the retry interceptor handle it.
             */
            throw new IllegalStateException(
                    "Could not serialize the validated Identity event.",
                    exception
            );
        }

        /*
         * This method is transactional.
         * It returns only after the inbox insert has committed.
         */
        boolean inserted = inboxEventService.accept(
                expectedEventType.name(),
                messageId,
                payload.userId(),
                payloadJson
        );

        /*
         * Acknowledge only after successful database commit.
         * If acknowledgement fails after the insert, RabbitMQ may
         * redeliver. The inbox unique constraint handles that safely.
         */
        channel.basicAck(
                deliveryTag,
                false
        );

        if (inserted) {

            log.info(
                    "Identity event accepted: eventType={}, messageId={}, userId={}",
                    expectedEventType,
                    messageId,
                    payload.userId()
            );

        } else {

            log.info(
                    "Duplicate Identity event acknowledged: eventType={}, messageId={}",
                    expectedEventType,
                    messageId
            );
        }
    }

    // PAYLOAD VALIDATION
    private void validatePayload(
            IdentityEventPayload payload,
            IdentityEventType expectedEventType
    ) {

        if (payload == null) {
            throw invalidMessage("Identity event payload is empty.");
        }

        if (!expectedEventType.name().equals(payload.eventType())) {
            throw invalidMessage(
                    "Identity event payload type does not match its queue."
            );
        }

        if (payload.userId() == null) {
            throw invalidMessage("Identity event user ID is missing.");
        }

        switch (payload) {
            case EmailVerificationMessage event -> {

                requireText(event.email(), "Email is required.");
                requireText(event.firstName(), "First name is required.");
                requireText(event.verificationToken(), "Verification token is required.");
                requireText(event.verificationUrl(), "Verification URL is required.");
                requireInstant(event.expiresAt(), "Verification expiry is required.");

                return;
            }
            case PasswordResetMessage event -> {

                requireText(event.email(), "Email is required.");
                requireText(event.firstName(), "First name is required.");
                requireText(event.resetToken(), "Reset token is required.");
                requireText(event.resetUrl(), "Reset URL is required.");
                requireInstant(event.expiresAt(), "Reset expiry is required.");

                return;
            }
            case StaffOnboardingMessage event -> {

                requireText(event.email(), "Email is required.");
                requireText(event.firstName(), "First name is required.");
                requireText(event.onboardingToken(), "Onboarding token is required.");
                requireText(event.onboardingUrl(), "Onboarding URL is required.");
                requireInstant(event.expiresAt(), "Onboarding expiry is required.");

                return;
            }
            case CustomerConvertedToStaffMessage event -> {

                requireText(event.email(), "Email is required.");
                requireText(event.firstName(), "First name is required.");
                requireText(event.lastName(), "Last name is required.");
                requireText(event.employeeNumber(), "Employee number is required.");
                requireText(event.staffRole(), "Staff role is required.");
                requireInstant(event.convertedAt(), "Conversion timestamp is required.");
                requireText(event.deliveryPolicy(), "Delivery policy is required.");

                requireChannels(event.availableChannels());

                return;
            }
            case EmailChangeVerificationMessage event -> {

                requireText(event.oldEmail(), "Old email is required.");
                requireText(event.newEmail(), "New email is required.");
                requireText(event.firstName(), "First name is required.");
                requireText(event.verificationToken(), "Verification token is required.");
                requireText(event.verificationUrl(), "Verification URL is required.");
                requireInstant(event.expiresAt(), "Verification expiry is required.");

                return;
            }
            case EmailChangedConfirmedMessage event -> {

                requireText(event.oldEmail(), "Old email is required.");
                requireText(event.newEmail(), "New email is required.");
                requireText(event.firstName(), "First name is required.");
                requireText(event.lastName(), "Last name is required.");
                requireInstant(event.emailChangedAt(), "Email change timestamp is required.");
                requireInstant(
                        event.nextEmailChangeAllowedAt(),
                        "Next permitted email change timestamp is required."
                );

                requireText(event.deliveryPolicy(), "Delivery policy is required.");
                requireChannels(event.availableChannels());

                return;
            }
            default -> {
            }
        }

        throw invalidMessage(
                "Unsupported Identity event payload type."
        );
    }

    private void requireText(
            String value,
            String message
    ) {

        if (value == null || value.isBlank()) {
            throw invalidMessage(message);
        }
    }

    private void requireInstant(
            Instant value,
            String message
    ) {

        if (value == null) {
            throw invalidMessage(message);
        }
    }

    private void requireChannels(
            List<String> channels
    ) {

        if (channels == null || channels.isEmpty()) {
            throw invalidMessage(
                    "At least one notification channel must be supplied."
            );
        }

        List<String> allowedChannels = List.of(
                "EMAIL",
                "PUSH",
                "IN_APP",
                "WEBSOCKET"
        );

        boolean containsUnsupportedChannel = channels.stream()
                .anyMatch(channel ->
                        channel == null
                                || !allowedChannels.contains(channel)
                );

        if (containsUnsupportedChannel) {
            throw invalidMessage(
                    "Identity event contains an unsupported notification channel."
            );
        }
    }

    private AmqpRejectAndDontRequeueException invalidMessage(
            String reason
    ) {
        return new AmqpRejectAndDontRequeueException(reason);
    }
}