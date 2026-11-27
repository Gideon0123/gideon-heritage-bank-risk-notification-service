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
                ),
                @Index(
                        name = "idx_notification_delivery_expired_lease",
                        columnList = "locked_until"
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
    private NotificationDeliveryStatus status = NotificationDeliveryStatus.PENDING;

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
    private NotificationContentFormat contentFormat = NotificationContentFormat.PLAIN_TEXT;

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

    /*
     * Worker lease.
     *
     * Only PROCESSING deliveries may have these fields populated.
     */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(
            name = "locked_by",
            length = 150
    )
    private String lockedBy;

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

    // FACTORY
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
            throw new IllegalArgumentException("Delivery channel is required.");
        }

        if (encryptedContent == null || encryptedContent.isBlank()) {
            throw new IllegalArgumentException("Encrypted delivery content is required.");
        }

        if (contentFormat == null) {
            throw new IllegalArgumentException("Delivery content format is required.");
        }

        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Maximum attempts must be at least 1.");
        }

        if (channel != NotificationChannel.IN_APP && (encryptedDestination == null
                || encryptedDestination.isBlank())) {

            throw new IllegalArgumentException("Encrypted destination is required for this channel.");
        }

        if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > 255)) {

            throw new IllegalArgumentException("Delivery idempotency key is invalid.");
        }

        if (template != null && template.getChannel() != channel) {
            throw new IllegalArgumentException("Delivery channel must match the template channel.");
        }

        if (channel == NotificationChannel.EMAIL && (subject == null || subject.isBlank())) {

            throw new IllegalArgumentException("Email delivery subject is required.");
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

    // RELATIONSHIP MANAGEMENT
    void assignNotification(Notification notification) {

        if (notification == null) {
            throw new IllegalArgumentException("Notification is required.");
        }

        this.notification = notification;
    }

    // DELIVERY CLAIM
    public void markProcessing(
            String workerId,
            Instant leaseExpiry
    ) {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("Delivery worker ID is required.");
        }

        if (workerId.length() > 150) {
            throw new IllegalArgumentException("Delivery worker ID cannot exceed 150 characters.");
        }

        if (leaseExpiry == null || !leaseExpiry.isAfter(Instant.now())) {
            throw new IllegalArgumentException("Delivery lease expiry must be in the future.");
        }

        if (status != NotificationDeliveryStatus.PENDING
                && status != NotificationDeliveryStatus.RETRY_SCHEDULED) {

            throw new IllegalStateException(
                    "Only PENDING or RETRY_SCHEDULED deliveries can be claimed."
            );
        }

        if (nextAttemptAt != null && nextAttemptAt.isAfter(Instant.now())) {

            throw new IllegalStateException("Delivery is not ready for another attempt.");
        }

        if (attemptCount >= maxAttempts) {
            throw new IllegalStateException("Maximum delivery attempts have been reached.");
        }

        this.status = NotificationDeliveryStatus.PROCESSING;
        this.attemptCount++;
        this.lastAttemptAt = Instant.now();
        this.nextAttemptAt = null;
        this.lastError = null;

        this.lockedBy = workerId;
        this.lockedUntil = leaseExpiry;
    }


    public void deferUntil(Instant nextAttemptAt) {
        if (status != NotificationDeliveryStatus.PENDING) {
            throw new IllegalStateException("Only a pending delivery can be deferred before its first attempt.");
        }
        if (nextAttemptAt == null || !nextAttemptAt.isAfter(Instant.now())) return;
        this.nextAttemptAt = nextAttemptAt;
    }

    // SUCCESSFUL SMTP SUBMISSION
    public void markSent(
            String provider,
            String providerMessageId
    ) {
        requireStatus(NotificationDeliveryStatus.PROCESSING);

        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Delivery provider is required.");
        }

        this.status = NotificationDeliveryStatus.SENT;
        this.provider = provider;
        this.providerMessageId = providerMessageId;
        this.sentAt = Instant.now();

        this.nextAttemptAt = null;
        this.lastError = null;

        clearLease();
    }

    // PROVIDER DELIVERY CONFIRMATION
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

        clearLease();
    }

    // RETRY
    public void scheduleRetry(
            Instant nextAttemptAt,
            String error
    ) {
        requireStatus(NotificationDeliveryStatus.PROCESSING);

        if (attemptCount >= maxAttempts) {
            markDeadLettered(error);
            return;
        }

        if (nextAttemptAt == null || !nextAttemptAt.isAfter(Instant.now())) {

            throw new IllegalArgumentException("Next delivery attempt must be in the future.");
        }

        this.status = NotificationDeliveryStatus.RETRY_SCHEDULED;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = sanitizeError(error);

        clearLease();
    }

    // PERMANENT FAILURE
    public void markFailed(String error) {

        if (status == NotificationDeliveryStatus.DELIVERED
                || status == NotificationDeliveryStatus.SENT
                || status == NotificationDeliveryStatus.CANCELLED) {

            throw new IllegalStateException(
                    "Sent, delivered or cancelled deliveries cannot be marked as failed."
            );
        }

        this.status = NotificationDeliveryStatus.FAILED;
        this.failedAt = Instant.now();
        this.nextAttemptAt = null;
        this.lastError = sanitizeError(error);

        clearLease();
    }

    // EXHAUSTED RETRIES
    public void markDeadLettered(String error) {

        if (status == NotificationDeliveryStatus.SENT
                || status == NotificationDeliveryStatus.DELIVERED
                || status == NotificationDeliveryStatus.CANCELLED) {

            throw new IllegalStateException(
                    "Sent, delivered or cancelled deliveries cannot be dead-lettered."
            );
        }

        this.status = NotificationDeliveryStatus.DEAD_LETTERED;
        this.failedAt = Instant.now();
        this.nextAttemptAt = null;
        this.lastError = sanitizeError(error);

        clearLease();
    }

    // EXPIRED LEASE RECOVERY
    public void recoverExpiredLease(
            Instant now,
            Instant nextAttemptAt,
            String error
    ) {

        requireStatus(NotificationDeliveryStatus.PROCESSING);

        if (lockedUntil == null || lockedUntil.isAfter(now)) {
            throw new IllegalStateException("Delivery lease has not expired.");
        }

        if (attemptCount >= maxAttempts) {

            markDeadLettered(
                    error + " Maximum delivery attempts reached."
            );

            return;
        }

        if (nextAttemptAt == null || !nextAttemptAt.isAfter(now)) {
            throw new IllegalArgumentException("Next delivery attempt must be in the future.");
        }

        this.status = NotificationDeliveryStatus.RETRY_SCHEDULED;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = sanitizeError(error);

        clearLease();
    }

    // CANCELLATION
    public void cancel(String reason) {

        if (status == NotificationDeliveryStatus.DELIVERED || status == NotificationDeliveryStatus.SENT) {

            throw new IllegalStateException("A sent or delivered notification cannot be cancelled.");
        }

        this.status = NotificationDeliveryStatus.CANCELLED;
        this.nextAttemptAt = null;
        this.lastError = sanitizeError(reason);

        clearLease();
    }

    // HELPERS
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
            throw new IllegalStateException("Delivery must be in " + expected + " state.");
        }
    }

    private void clearLease() {
        this.lockedBy = null;
        this.lockedUntil = null;
    }

    private String sanitizeError(String error) {

        if (error == null || error.isBlank()) {
            return "Unknown notification delivery failure.";
        }

        String sanitized = error
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();

        if (sanitized.length() > 5000) {
            sanitized = sanitized.substring(0, 5000);
        }

        return sanitized;
    }
}