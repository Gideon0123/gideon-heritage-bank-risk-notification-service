package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationCategory;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "notifications",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_notification_source_event",
                        columnNames = {
                                "user_id",
                                "source_event_type",
                                "source_message_id"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_notifications_user_created",
                        columnList = "user_id, created_at"
                ),
                @Index(
                        name = "idx_notifications_user_read",
                        columnList = "user_id, read_at"
                ),
                @Index(
                        name = "idx_notifications_user_category",
                        columnList = "user_id, category"
                ),
                @Index(
                        name = "idx_notifications_expiry",
                        columnList = "expires_at"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    /*
     * This is the UUID of the user from Identity Service.
     *
     * We intentionally do not create a JPA relationship
     * to BankUser because Identity Service owns that entity
     * and its database.
     */
    @Column(
            name = "user_id",
            nullable = false
    )
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "notification_type",
            nullable = false,
            length = 60
    )
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "category",
            nullable = false,
            length = 30
    )
    private NotificationCategory category;

    /*
     * Original event information.
     *
     * These fields allow us to trace a notification
     * back to the event that generated it.
     */
    @Column(
            name = "source_event_type",
            length = 100
    )
    private String sourceEventType;

    @Column(
            name = "source_message_id",
            length = 255
    )
    private String sourceMessageId;

    /*
     * Direct reference to the durable inbox event.
     *
     * This is only an identifier, not a JPA relationship.
     */
    @Column(name = "source_inbox_event_id")
    private UUID sourceInboxEventId;

    @Column(
            name = "title",
            nullable = false,
            length = 255
    )
    private String title;

    @Column(
            name = "body",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String body;

    @Column(
            name = "action_url",
            length = 1000
    )
    private String actionUrl;

    /*
     * Optional additional notification data.
     *
     * Example:
     * {
     *   "accountNumber": "1234567890",
     *   "transactionReference": "TXN-001"
     * }
     *
     * Store serialized JSON here. Do not store secrets
     * or authentication tokens in this field.
     */
    @Column(
            name = "metadata_json",
            columnDefinition = "TEXT"
    )
    private String metadataJson;

    @CreationTimestamp
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    /*
     * Null means the user has not read the notification.
     */
    @Column(name = "read_at")
    private Instant readAt;

    /*
     * Archiving is different from deleting.
     * An archived notification can still be retained.
     */
    @Column(name = "archived_at")
    private Instant archivedAt;

    /*
     * Optimistic locking prevents lost updates when
     * multiple requests update the same notification.
     */
    @Version
    @Column(
            name = "version",
            nullable = false
    )
    private long version;

    /*
     * A notification may have multiple delivery records:
     * EMAIL, IN_APP, SMS, PUSH.
     */
    @OneToMany(
            mappedBy = "notification",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<NotificationDelivery> deliveries = new ArrayList<>();


    // =====================================================
    // FACTORY METHOD
    // =====================================================

    public static Notification create(
            UUID userId,
            NotificationType notificationType,
            NotificationCategory category,
            String sourceEventType,
            String sourceMessageId,
            UUID sourceInboxEventId,
            String title,
            String body,
            String actionUrl,
            String metadataJson,
            Instant expiresAt
    ) {

        if (userId == null) {
            throw new IllegalArgumentException(
                    "Notification user ID is required."
            );
        }

        if (notificationType == null) {
            throw new IllegalArgumentException(
                    "Notification type is required."
            );
        }

        if (category == null) {
            throw new IllegalArgumentException(
                    "Notification category is required."
            );
        }

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException(
                    "Notification title is required."
            );
        }

        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException(
                    "Notification body is required."
            );
        }

        if (title.length() > 255) {
            throw new IllegalArgumentException(
                    "Notification title cannot exceed 255 characters."
            );
        }

        if (actionUrl != null && actionUrl.length() > 1000) {
            throw new IllegalArgumentException(
                    "Notification action URL cannot exceed 1000 characters."
            );
        }

        if ((sourceEventType == null) != (sourceMessageId == null)) {
            throw new IllegalArgumentException(
                    "Source event type and source message ID must either both be provided or both be null."
            );
        }

        if (sourceEventType != null && sourceEventType.isBlank()) {
            throw new IllegalArgumentException(
                    "Source event type cannot be blank."
            );
        }

        if (sourceMessageId != null && sourceMessageId.isBlank()) {
            throw new IllegalArgumentException(
                    "Source message ID cannot be blank."
            );
        }

        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            throw new IllegalArgumentException(
                    "Notification expiry cannot be in the past."
            );
        }

        Notification notification = new Notification();

        notification.userId = userId;
        notification.notificationType = notificationType;
        notification.category = category;
        notification.sourceEventType = sourceEventType;
        notification.sourceMessageId = sourceMessageId;
        notification.sourceInboxEventId = sourceInboxEventId;
        notification.title = title.trim();
        notification.body = body;
        notification.actionUrl = actionUrl;
        notification.metadataJson = metadataJson;
        notification.expiresAt = expiresAt;

        return notification;
    }


    // =====================================================
    // DELIVERY MANAGEMENT
    // =====================================================

    public void addDelivery(NotificationDelivery delivery) {

        if (delivery == null) {
            throw new IllegalArgumentException(
                    "Notification delivery is required."
            );
        }

        if (deliveries.stream().anyMatch(
                existing -> existing.getChannel() == delivery.getChannel()
        )) {
            throw new IllegalArgumentException(
                    "A delivery for this notification channel already exists."
            );
        }

        deliveries.add(delivery);
        delivery.assignNotification(this);
    }


    // =====================================================
    // NOTIFICATION STATE MANAGEMENT
    // =====================================================

    public void markAsRead() {

        if (readAt == null) {
            readAt = Instant.now();
        }
    }

    public void archive() {

        if (archivedAt == null) {
            archivedAt = Instant.now();
        }
    }

    public void restore() {

        archivedAt = null;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public boolean isExpired() {

        return expiresAt != null
                && expiresAt.isBefore(Instant.now());
    }
}
