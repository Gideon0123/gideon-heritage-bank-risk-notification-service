package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.InboxEventStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "notification_inbox_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_notification_inbox_event_identity",
                        columnNames = {
                                "event_type",
                                "message_id"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_notification_inbox_status_received",
                        columnList = "status, received_at"
                ),
                @Index(
                        name = "idx_notification_inbox_user",
                        columnList = "user_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboxEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(
            name = "event_type",
            nullable = false,
            length = 100
    )
    private String eventType;

    @Column(
            name = "message_id",
            nullable = false,
            length = 255
    )
    private String messageId;

    @Column(
            name = "user_id",
            nullable = false
    )
    private UUID userId;

    /*
     * The complete original event payload is encrypted before
     * it is persisted.
     */
    @Column(
            name = "encrypted_payload",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String encryptedPayload;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 20
    )
    private InboxEventStatus status = InboxEventStatus.RECEIVED;

    @Column(
            name = "attempts",
            nullable = false
    )
    private int attempts;

    @CreationTimestamp
    @Column(
            name = "received_at",
            nullable = false,
            updatable = false
    )
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "last_error", length = 150)
    private String lastError;

    @Version
    @Column(
            name = "version",
            nullable = false
    )
    private long version;
}