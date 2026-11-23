package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.*;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.*;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.messaging.contract.*;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdentityEventNotificationFactory {

    private static final String LOCALE = "en-NG";

    /*
     * Delivery attempts are separate from outbox publishing attempts.
     * These attempts will be used by the future delivery consumers.
     */
    private static final int DELIVERY_MAX_ATTEMPTS = 5;

    private final NotificationTemplateRenderingService templateRenderingService;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationContentEncryptionService contentEncryptionService;
    private final NotificationOutboxService outboxService;

    public void createNotification(
            InboxEvent inboxEvent,
            IdentityEventPayload payload
    ) {

        if (inboxEvent == null || payload == null) {
            throw new IllegalArgumentException(
                    "Inbox event and Identity payload are required."
            );
        }

        NotificationDefinition definition = defineNotification(payload);

        if (definition.expiresAt() != null
                && !definition.expiresAt().isAfter(Instant.now())) {

            log.warn(
                    "Expired Identity notification event skipped. "
                            + "eventType={}, messageId={}",
                    inboxEvent.getEventType(),
                    inboxEvent.getMessageId()
            );

            return;
        }

        Notification notification = Notification.create(
                inboxEvent.getUserId(),
                definition.notificationType(),
                definition.category(),
                inboxEvent.getEventType(),
                inboxEvent.getMessageId(),
                inboxEvent.getId(),
                definition.notificationTitle(),
                definition.notificationBody(),
                null,
                null,
                definition.expiresAt()
        );

        List<NotificationChannel> channels = resolveChannels(definition.requestedChannels());

        for (NotificationChannel channel : channels) {

            /*
             * No preference record means the channel is enabled
             * by default.
             */
            if (!isChannelEnabled(inboxEvent.getUserId(), definition.category(), channel)) {

                log.info(
                        "Notification channel skipped due to user preference. "
                                + "userId={}, channel={}, category={}",
                        inboxEvent.getUserId(),
                        channel,
                        definition.category()
                );

                continue;
            }

            createDelivery(
                    notification,
                    inboxEvent,
                    definition,
                    channel
            );
        }

        /*
         * This saves the notification and all attached deliveries
         * and outbox records atomically.
         *
         * If all delivery channels were disabled or unsupported,
         * the notification itself is still persisted.
         */
        outboxService.createNotificationWithDeliveries(notification);
    }

    private void createDelivery(
            Notification notification,
            InboxEvent inboxEvent,
            NotificationDefinition definition,
            NotificationChannel channel
    ) {

        RenderedNotificationTemplate rendered =
                templateRenderingService.render(
                        definition.templateKey(),
                        channel,
                        definition.templateVariables()
                );

        String destination = null;
        String subject = null;
        String content;

        if (channel == NotificationChannel.EMAIL) {

            if (definition.emailDestination() == null || definition.emailDestination().isBlank()) {

                throw new IllegalStateException(
                        "Email destination is missing for an email notification."
                );
            }

            destination = contentEncryptionService.encryptDestination(
                    definition.emailDestination()
            );

            subject = rendered.subject();

            content = rendered.body();

        } else if (channel == NotificationChannel.IN_APP) {

            String title = rendered.title() == null
                    ? definition.notificationTitle()
                    : rendered.title();

            content = title + "\n\n" + rendered.body();

        } else {

            /*
             * PUSH is deliberately not created here.
             *
             * Your Identity event contracts contain channel names,
             * but they do not contain a push-device token or a
             * device-registration identifier.
             *
             * We must not create a PUSH delivery with a fabricated
             * destination. A device-token source is required first.
             */
            log.warn(
                    "Notification channel is not currently supported "
                            + "by the Identity notification factory. "
                            + "channel={}, userId={}",
                    channel,
                    inboxEvent.getUserId()
            );

            return;
        }

        String encryptedContent =
                contentEncryptionService.encryptContent(content);

        String encryptedDestination = destination;

        String idempotencyKey = createIdempotencyKey(
                inboxEvent.getEventType(),
                inboxEvent.getMessageId(),
                channel
        );

        NotificationDelivery delivery = NotificationDelivery.create(
                channel,
                rendered.template(),
                encryptedDestination,
                subject,
                encryptedContent,
                rendered.template().getContentFormat(),
                idempotencyKey,
                DELIVERY_MAX_ATTEMPTS
        );

        notification.addDelivery(delivery);
    }

    private boolean isChannelEnabled(
            UUID userId,
            NotificationCategory category,
            NotificationChannel channel
    ) {

        return preferenceRepository
                .findByUserIdAndCategoryAndChannel(
                        userId,
                        category,
                        channel
                )
                .map(NotificationPreference::isEnabled)
                .orElse(true);
    }

    /*
     * The first four Identity event types do not carry an
     * availableChannels field. For those events, we use EMAIL
     * and IN_APP as the default channels.
     *
     * The two event types that do carry availableChannels use
     * that list as the channel selection.
     *
     * WEBSOCKET is normalized to IN_APP. It is a delivery transport,
     * not a persisted NotificationChannel.
     */
    private List<NotificationChannel> resolveChannels(
            List<String> requestedChannels
    ) {

        List<String> channels = requestedChannels == null
                ? List.of("EMAIL", "IN_APP")
                : requestedChannels;

        Set<NotificationChannel> resolved =
                new LinkedHashSet<>();

        for (String requestedChannel : channels) {

            if (requestedChannel == null || requestedChannel.isBlank()) {
                continue;
            }

            switch (requestedChannel.trim().toUpperCase(Locale.ROOT)) {

                case "EMAIL" -> resolved.add(NotificationChannel.EMAIL);

                case "IN_APP", "WEBSOCKET" ->
                        resolved.add(NotificationChannel.IN_APP);

                case "PUSH" -> {

                    /*
                     * The event can request PUSH, but this project
                     * currently has no device-token resolver.
                     */
                    log.warn(
                            "PUSH requested for an Identity event, "
                                    + "but no push destination resolver exists yet."
                    );
                }

                default -> throw new IllegalArgumentException(
                        "Unsupported Identity notification channel: "
                                + requestedChannel
                );
            }
        }

        return List.copyOf(resolved);
    }

    private NotificationDefinition defineNotification(
            IdentityEventPayload payload
    ) {

        if (payload instanceof EmailVerificationMessage event) {

            Map<String, String> variables = variables(
                    "firstName", event.firstName(),
                    "email", event.email(),
                    "verificationToken", event.verificationToken(),
                    "verificationUrl", event.verificationUrl(),
                    "expiresAt", event.expiresAt()
            );

            return new NotificationDefinition(
                    NotificationType.EMAIL_VERIFICATION,
                    NotificationCategory.SECURITY,
                    "identity.email-verification",
                    "Verify your email address",
                    "Complete email verification to activate your "
                            + "Gideon Heritage Bank identity.",
                    event.email(),
                    event.expiresAt(),
                    variables,
                    null
            );
        }

        if (payload instanceof PasswordResetMessage event) {

            Map<String, String> variables = variables(
                    "firstName", event.firstName(),
                    "email", event.email(),
                    "resetToken", event.resetToken(),
                    "resetUrl", event.resetUrl(),
                    "expiresAt", event.expiresAt()
            );

            return new NotificationDefinition(
                    NotificationType.PASSWORD_RESET,
                    NotificationCategory.SECURITY,
                    "identity.password-reset",
                    "Password reset requested",
                    "A password reset was requested for your banking "
                            + "identity. If you did not request this, "
                            + "secure your account.",
                    event.email(),
                    event.expiresAt(),
                    variables,
                    null
            );
        }

        if (payload instanceof StaffOnboardingMessage event) {

            Map<String, String> variables = variables(
                    "firstName", event.firstName(),
                    "email", event.email(),
                    "onboardingToken", event.onboardingToken(),
                    "onboardingUrl", event.onboardingUrl(),
                    "expiresAt", event.expiresAt()
            );

            return new NotificationDefinition(
                    NotificationType.STAFF_ONBOARDING,
                    NotificationCategory.SECURITY,
                    "identity.staff-onboarding",
                    "Complete staff onboarding",
                    "Your staff identity is ready for onboarding. "
                            + "Complete the onboarding process to activate "
                            + "your staff access.",
                    event.email(),
                    event.expiresAt(),
                    variables,
                    null
            );
        }

        if (payload instanceof CustomerConvertedToStaffMessage event) {

            Map<String, String> variables = variables(
                    "firstName", event.firstName(),
                    "lastName", event.lastName(),
                    "email", event.email(),
                    "employeeNumber", event.employeeNumber(),
                    "staffRole", event.staffRole(),
                    "convertedAt", event.convertedAt(),
                    "deliveryPolicy", event.deliveryPolicy(),
                    "availableChannels",
                    event.availableChannels() == null
                            ? null
                            : String.join(",", event.availableChannels())
            );

            return new NotificationDefinition(
                    NotificationType.CUSTOMER_CONVERTED_TO_STAFF,
                    NotificationCategory.SECURITY,
                    "identity.customer-converted-to-staff",
                    "Your identity is now a staff identity",
                    "Your customer identity has been converted to a "
                            + "staff identity. Review your staff details.",
                    event.email(),
                    null,
                    variables,
                    event.availableChannels()
            );
        }

        if (payload instanceof EmailChangeVerificationMessage event) {

            Map<String, String> variables = variables(
                    "firstName", event.firstName(),
                    "oldEmail", event.oldEmail(),
                    "newEmail", event.newEmail(),
                    "verificationToken", event.verificationToken(),
                    "verificationUrl", event.verificationUrl(),
                    "expiresAt", event.expiresAt()
            );

            return new NotificationDefinition(
                    NotificationType.EMAIL_CHANGE_VERIFICATION,
                    NotificationCategory.SECURITY,
                    "identity.email-change-verification",
                    "Verify your new email address",
                    "A request was made to change your registered email "
                            + "address. Verify the new address to complete "
                            + "the change.",
                    event.newEmail(),
                    event.expiresAt(),
                    variables,
                    null
            );
        }

        if (payload instanceof EmailChangedConfirmedMessage event) {

            Map<String, String> variables = variables(
                    "firstName", event.firstName(),
                    "lastName", event.lastName(),
                    "oldEmail", event.oldEmail(),
                    "newEmail", event.newEmail(),
                    "emailChangedAt", event.emailChangedAt(),
                    "nextEmailChangeAllowedAt",
                    event.nextEmailChangeAllowedAt(),
                    "deliveryPolicy", event.deliveryPolicy(),
                    "availableChannels",
                    event.availableChannels() == null
                            ? null
                            : String.join(",", event.availableChannels())
            );

            return new NotificationDefinition(
                    NotificationType.EMAIL_CHANGED,
                    NotificationCategory.SECURITY,
                    "identity.email-changed",
                    "Your email address has changed",
                    "Your registered email address was changed "
                            + "successfully. Contact the bank immediately "
                            + "if you did not request this change.",
                    event.newEmail(),
                    null,
                    variables,
                    event.availableChannels()
            );
        }

        throw new IllegalArgumentException(
                "Unsupported Identity event payload: "
                        + payload.getClass().getSimpleName()
        );
    }

    private Map<String, String> variables(Object... values) {

        if (values.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "Template variables must be supplied as key/value pairs."
            );
        }

        Map<String, String> variables = new HashMap<>();

        for (int i = 0; i < values.length; i += 2) {

            String key = (String) values[i];
            Object value = values[i + 1];

            if (value != null) {
                variables.put(
                        key,
                        value.toString()
                );
            }
        }

        return Collections.unmodifiableMap(variables);
    }

    private String createIdempotencyKey(
            String eventType,
            String messageId,
            NotificationChannel channel
    ) {

        String source = eventType
                + "|"
                + messageId
                + "|"
                + channel.name();

        try {

            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    source.getBytes(StandardCharsets.UTF_8)
            );

            return "identity-" + HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                    "SHA-256 is not available.",
                    exception
            );
        }
    }

    private record NotificationDefinition(

            NotificationType notificationType,

            NotificationCategory category,

            String templateKey,

            String notificationTitle,

            String notificationBody,

            String emailDestination,

            Instant expiresAt,

            Map<String, String> templateVariables,

            List<String> requestedChannels

    ) {
    }
}