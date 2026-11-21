package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationChannel;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationContentFormat;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationDeliveryStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "notification_deliveries",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_notification_delivery_channel",
                        columnNames = {
                                "notification_id",
                                "channel"
                        }
                ),
                @UniqueConstraint(
                        name = "uq_notification_delivery_idempotency",
                        columnNames = "idempotency_key"
                )
        },
        indexes = {
                @Index(
                        name = "idx_notification_delivery_status_next_attempt",
                        columnList = "status, next_attempt_at"
                ),
                @Index(
                        name = "idx_notification_delivery_notification",
                        columnList = "notification_id"
                ),
                @Index(
                        name = "idx_notification_delivery_provider_message",
                        columnList = "provider_message_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationDelivery {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "notification_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_notification_delivery_notification"
            )
    )
    private Notification notification;

    /*
     * The exact template version used to render this delivery.
     * It can be null for manually composed notifications.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "template_id",
            foreignKey = @ForeignKey(
                    name = "fk_notification_delivery_template"
            )
    )
    private NotificationTemplate template;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "channel",
            nullable = false,
            length = 20
    )
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 30
    )
    private NotificationDeliveryStatus status =
            NotificationDeliveryStatus.PENDING;

    /*
     * Destination snapshot.
     *
     * EMAIL: recipient email address.
     * SMS: recipient phone number.
     * PUSH: destination token or provider-specific identifier.
     * IN_APP: normally null because userId is on Notification.
     *
     * This is a snapshot so a later profile change does not
     * silently change the destination of an already-created delivery.
     */
    @Column(
            name = "destination",
            length = 500
    )
    private String destination;

    @Column(
            name = "subject",
            length = 500
    )
    private String subject;

    @Column(
            name = "content",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "content_format",
            nullable = false,
            length = 20
    )
    private NotificationContentFormat contentFormat =
            NotificationContentFormat.PLAIN_TEXT;

    /*
     * Provider information.
     *
     * Examples:
     * SMTP
     * SENDGRID
     * AWS_SES
     * INTERNAL_WEBSOCKET
     */
    @Column(
            name = "provider",
            length = 80
    )
    private String provider;

    @Column(
            name = "provider_message_id",
            length = 255
    )
    private String providerMessageId;

    /*
     * Unique key used to prevent duplicate delivery creation.
     */
    @Column(
            name = "idempotency_key",
            length = 255
    )
    private String idempotencyKey;

    /*
     * Retry information.
     */
    @Column(
            name = "attempt_count",
            nullable = false
    )
    private int attemptCount = 0;

    @Column(
            name = "max_attempts",
            nullable = false
    )
    private int maxAttempts = 5;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(
            name = "last_error",
            columnDefinition = "TEXT"
    )
    private String lastError;

    @CreationTimestamp
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @UpdateTimestamp
    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    @Version
    @Column(
            name = "version",
            nullable = false
    )
    private long version;


    // =====================================================
    // FACTORY
    // =====================================================

    public static NotificationDelivery create(
            NotificationChannel channel,
            NotificationTemplate template,
            String destination,
            String subject,
            String content,
            NotificationContentFormat contentFormat,
            String idempotencyKey,
            int maxAttempts
    ) {

        if (channel == null) {
            throw new IllegalArgumentException(
                    "Delivery channel is required."
            );
        }

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException(
                    "Delivery content is required."
            );
        }

        if (contentFormat == null) {
            throw new IllegalArgumentException(
                    "Delivery content format is required."
            );
        }

        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "Maximum attempts must be at least 1."
            );
        }

        if (channel != NotificationChannel.IN_APP
                && (destination == null || destination.isBlank())) {

            throw new IllegalArgumentException(
                    "Delivery destination is required for this channel."
            );
        }

        if (destination != null && destination.length() > 500) {
            throw new IllegalArgumentException(
                    "Delivery destination cannot exceed 500 characters."
            );
        }

        if (subject != null && subject.length() > 500) {
            throw new IllegalArgumentException(
                    "Delivery subject cannot exceed 500 characters."
            );
        }

        if (idempotencyKey != null
                && (idempotencyKey.isBlank() || idempotencyKey.length() > 255)) {

            throw new IllegalArgumentException(
                    "Delivery idempotency key is invalid."
            );
        }

        if (template != null && template.getChannel() != channel) {
            throw new IllegalArgumentException(
                    "Delivery channel must match the template channel."
            );
        }

        NotificationDelivery delivery = new NotificationDelivery();

        delivery.channel = channel;
        delivery.template = template;
        delivery.destination = destination;
        delivery.subject = subject;
        delivery.content = content;
        delivery.contentFormat = contentFormat;
        delivery.idempotencyKey = idempotencyKey;
        delivery.maxAttempts = maxAttempts;
        delivery.status = NotificationDeliveryStatus.PENDING;

        return delivery;
    }


    // =====================================================
    // RELATIONSHIP MANAGEMENT
    // =====================================================

    void assignNotification(Notification notification) {

        if (notification == null) {
            throw new IllegalArgumentException(
                    "Notification is required."
            );
        }

        this.notification = notification;
    }


    // =====================================================
    // DELIVERY LIFECYCLE
    // =====================================================

    public void markProcessing() {

        if (isTerminal()) {
            throw new IllegalStateException(
                    "A terminal delivery cannot be processed."
            );
        }

        if (attemptCount >= maxAttempts) {
            throw new IllegalStateException(
                    "Maximum delivery attempts have been reached."
            );
        }

        this.status = NotificationDeliveryStatus.PROCESSING;
        this.attemptCount++;
        this.lastAttemptAt = Instant.now();
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    public void markSent(
            String provider,
            String providerMessageId
    ) {

        requireStatus(NotificationDeliveryStatus.PROCESSING);

        this.status = NotificationDeliveryStatus.SENT;
        this.provider = provider;
        this.providerMessageId = providerMessageId;
        this.sentAt = Instant.now();
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    public void markDelivered() {

        if (status != NotificationDeliveryStatus.SENT
                && status != NotificationDeliveryStatus.PROCESSING) {

            throw new IllegalStateException(
                    "Only sent or processing deliveries can be marked as delivered."
            );
        }

        this.status = NotificationDeliveryStatus.DELIVERED;
        this.deliveredAt = Instant.now();
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    public void scheduleRetry(
            Instant nextAttemptAt,
            String error
    ) {

        requireStatus(NotificationDeliveryStatus.PROCESSING);

        if (attemptCount >= maxAttempts) {
            markFailed(error);
            return;
        }

        if (nextAttemptAt == null
                || nextAttemptAt.isBefore(Instant.now())) {

            throw new IllegalArgumentException(
                    "Next attempt time must be in the future."
            );
        }

        this.status = NotificationDeliveryStatus.RETRY_SCHEDULED;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = error;
    }

    public void markFailed(String error) {

        if (status == NotificationDeliveryStatus.DELIVERED
                || status == NotificationDeliveryStatus.CANCELLED) {

            throw new IllegalStateException(
                    "Delivered or cancelled deliveries cannot be marked as failed."
            );
        }

        this.status = NotificationDeliveryStatus.FAILED;
        this.failedAt = Instant.now();
        this.nextAttemptAt = null;
        this.lastError = error;
    }

    public void markDeadLettered(String error) {

        if (status == NotificationDeliveryStatus.DELIVERED
                || status == NotificationDeliveryStatus.CANCELLED) {

            throw new IllegalStateException(
                    "Delivered or cancelled deliveries cannot be dead-lettered."
            );
        }

        this.status = NotificationDeliveryStatus.DEAD_LETTERED;
        this.nextAttemptAt = null;
        this.lastError = error;
    }

    public void cancel(String reason) {

        if (status == NotificationDeliveryStatus.DELIVERED) {
            throw new IllegalStateException(
                    "A delivered notification cannot be cancelled."
            );
        }

        this.status = NotificationDeliveryStatus.CANCELLED;
        this.nextAttemptAt = null;
        this.lastError = reason;
    }


    // =====================================================
    // HELPERS
    // =====================================================

    public boolean isTerminal() {

        return status == NotificationDeliveryStatus.DELIVERED
                || status == NotificationDeliveryStatus.FAILED
                || status == NotificationDeliveryStatus.DEAD_LETTERED
                || status == NotificationDeliveryStatus.CANCELLED;
    }

    public boolean hasAttemptsRemaining() {
        return attemptCount < maxAttempts;
    }

    private void requireStatus(NotificationDeliveryStatus expected) {

        if (status != expected) {
            throw new IllegalStateException(
                    "Delivery must be in " + expected + " state."
            );
        }
    }
}