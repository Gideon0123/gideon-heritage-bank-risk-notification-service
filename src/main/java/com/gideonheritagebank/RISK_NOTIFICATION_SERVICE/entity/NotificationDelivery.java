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
                        columnNames = {"notification_id", "channel"}
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
     * AES-GCM encrypted destination.
     *
     * For EMAIL, this contains an encrypted email address.
     * For SMS, this contains an encrypted phone number.
     * For PUSH, this contains an encrypted destination token.
     *
     * IN_APP normally does not need a destination because
     * the parent Notification already contains userId.
     */
    @Column(
            name = "encrypted_destination",
            columnDefinition = "TEXT"
    )
    private String encryptedDestination;

    @Column(
            name = "subject",
            length = 500
    )
    private String subject;

    /*
     * AES-GCM encrypted rendered content.
     *
     * This may contain password-reset URLs, verification
     * links or other sensitive notification content.
     */
    @Column(
            name = "encrypted_content",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String encryptedContent;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "content_format",
            nullable = false,
            length = 20
    )
    private NotificationContentFormat contentFormat =
            NotificationContentFormat.PLAIN_TEXT;

    @Column(name = "provider", length = 80)
    private String provider;

    @Column(
            name = "provider_message_id",
            length = 255
    )
    private String providerMessageId;

    @Column(
            name = "idempotency_key",
            length = 255
    )
    private String idempotencyKey;

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

    /*
     * The caller must encrypt destination and content
     * using InboxPayloadEncryptionService before calling
     * this factory.
     */
    public static NotificationDelivery create(
            NotificationChannel channel,
            NotificationTemplate template,
            String encryptedDestination,
            String subject,
            String encryptedContent,
            NotificationContentFormat contentFormat,
            String idempotencyKey,
            int maxAttempts
    ) {

        if (channel == null) {
            throw new IllegalArgumentException(
                    "Delivery channel is required."
            );
        }

        if (encryptedContent == null || encryptedContent.isBlank()) {
            throw new IllegalArgumentException(
                    "Encrypted delivery content is required."
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
                && (encryptedDestination == null
                || encryptedDestination.isBlank())) {

            throw new IllegalArgumentException(
                    "Encrypted destination is required for this channel."
            );
        }

        if (idempotencyKey != null
                && (idempotencyKey.isBlank()
                || idempotencyKey.length() > 255)) {

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
        delivery.encryptedDestination = encryptedDestination;
        delivery.subject = subject;
        delivery.encryptedContent = encryptedContent;
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
                || !nextAttemptAt.isAfter(Instant.now())) {

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