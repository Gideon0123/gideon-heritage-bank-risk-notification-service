
package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationOutboxStatus;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.exception.BadRequest;
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
        name = "notification_outbox",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_notification_outbox_delivery",
                        columnNames = "delivery_id"
                ),
                @UniqueConstraint(
                        name = "uq_notification_outbox_message",
                        columnNames = "message_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_notification_outbox_available",
                        columnList = "status, available_at, created_at"
                ),
                @Index(
                        name = "idx_notification_outbox_created",
                        columnList = "created_at"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationOutbox {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "delivery_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_notification_outbox_delivery"
            )
    )
    private NotificationDelivery delivery;

    @Column(
            name = "message_id",
            nullable = false,
            unique = true
    )
    private UUID messageId;

    @Column(
            name = "delivery_id",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private UUID deliveryId;

    @Column(
            name = "event_type",
            nullable = false,
            length = 100
    )
    private String eventType;

    @Column(
            name = "exchange_name",
            nullable = false,
            length = 255
    )
    private String exchangeName;

    @Column(
            name = "routing_key",
            nullable = false,
            length = 255
    )
    private String routingKey;

    @Column(
            name = "payload",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 30
    )
    private NotificationOutboxStatus status =
            NotificationOutboxStatus.PENDING;

    @Column(
            name = "attempt_count",
            nullable = false
    )
    private int attemptCount = 0;

    @Column(
            name = "max_attempts",
            nullable = false
    )
    private int maxAttempts = 10;

    @Column(
            name = "available_at",
            nullable = false
    )
    private Instant availableAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(
            name = "locked_by",
            length = 150
    )
    private String lockedBy;

    @Column(name = "published_at")
    private Instant publishedAt;

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

    // FACTORY

    public static NotificationOutbox create(
            NotificationDelivery delivery,
            String eventType,
            String exchangeName,
            String routingKey,
            String payload,
            int maxAttempts
    ) {

        if (delivery == null) {
            throw new IllegalArgumentException("Notification delivery is required.");
        }

        if (eventType == null || eventType.isBlank()) {
            throw new BadRequest("Outbox event type is required.");
        }

        if (exchangeName == null || exchangeName.isBlank()) {
            throw new BadRequest("Outbox exchange name is required.");
        }

        if (routingKey == null || routingKey.isBlank()) {
            throw new BadRequest("Outbox routing key is required.");
        }

        if (payload == null || payload.isBlank()) {
            throw new BadRequest("Outbox payload is required.");
        }

        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "Maximum publishing attempts must be at least 1."
            );
        }

        NotificationOutbox outbox = new NotificationOutbox();

        outbox.delivery = delivery;
        outbox.messageId = UUID.randomUUID();
        outbox.eventType = eventType;
        outbox.exchangeName = exchangeName;
        outbox.routingKey = routingKey;
        outbox.payload = payload;
        outbox.maxAttempts = maxAttempts;
        outbox.availableAt = Instant.now();
        outbox.status = NotificationOutboxStatus.PENDING;

        return outbox;
    }

    // OUTBOX LIFECYCLE
    public void markProcessing(
            String workerId,
            Instant leaseExpiry
    ) {

        if (workerId == null || workerId.isBlank()) {
            throw new BadRequest("Worker ID is required.");
        }

        if (leaseExpiry == null || !leaseExpiry.isAfter(Instant.now())) {
            throw new BadRequest("Lease expiry must be in the future.");
        }

        if (status == NotificationOutboxStatus.PUBLISHED
                || status == NotificationOutboxStatus.DEAD_LETTERED) {

            throw new BadRequest("A terminal outbox record cannot be processed.");
        }

        if (attemptCount >= maxAttempts) {
            throw new IllegalStateException(
                    "Maximum publishing attempts have been reached."
            );
        }

        this.status = NotificationOutboxStatus.PROCESSING;
        this.attemptCount++;
        this.lockedBy = workerId;
        this.lockedUntil = leaseExpiry;
        this.lastError = null;
    }

    public void markPublished() {

        requireStatus(NotificationOutboxStatus.PROCESSING);

        this.status = NotificationOutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();

        clearLease();
        this.lastError = null;
    }

    public void scheduleRetry(
            Instant nextAttemptAt,
            String error
    ) {

        requireStatus(NotificationOutboxStatus.PROCESSING);

        if (attemptCount >= maxAttempts) {
            markDeadLettered(error);
            return;
        }

        if (nextAttemptAt == null || !nextAttemptAt.isAfter(Instant.now())) {

            throw new BadRequest("Next attempt time must be in the future.");
        }

        this.status = NotificationOutboxStatus.RETRY_SCHEDULED;
        this.availableAt = nextAttemptAt;
        this.lastError = error;

        clearLease();
    }

    public void markDeadLettered(String error) {

        if (status == NotificationOutboxStatus.PUBLISHED) {
            throw new BadRequest("A published outbox record cannot be dead-lettered.");
        }

        this.status = NotificationOutboxStatus.DEAD_LETTERED;
        this.lastError = error;

        clearLease();
    }

    public void recoverExpiredLease() {

        if (status != NotificationOutboxStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Only processing records can have their leases recovered."
            );
        }

        if (lockedUntil == null || lockedUntil.isAfter(Instant.now())) {

            throw new IllegalStateException("The outbox lease has not expired.");
        }

        clearLease();

        if (attemptCount >= maxAttempts) {
            markDeadLettered(
                    "Maximum publishing attempts reached after worker lease expiry."
            );
            return;
        }

        this.status = NotificationOutboxStatus.RETRY_SCHEDULED;
        this.availableAt = Instant.now();
        this.lastError = "Previous worker lease expired before completion.";
    }

    // HELPERS

    public boolean isTerminal() {

        return status == NotificationOutboxStatus.PUBLISHED
                || status == NotificationOutboxStatus.DEAD_LETTERED;
    }

    public boolean hasAttemptsRemaining() {
        return attemptCount < maxAttempts;
    }

    private void clearLease() {
        this.lockedBy = null;
        this.lockedUntil = null;
    }

    private void requireStatus(NotificationOutboxStatus expected) {

        if (status != expected) {
            throw new IllegalStateException(
                    "Outbox record must be in " + expected + " state."
            );
        }
    }
}