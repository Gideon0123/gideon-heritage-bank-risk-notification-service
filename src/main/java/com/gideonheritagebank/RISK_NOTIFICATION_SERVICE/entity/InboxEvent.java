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

    @Column(
            name = "next_attempt_at"
    )
    private Instant nextAttemptAt;

    @Column(
            name = "last_error",
            length = 150
    )
    private String lastError;

    @Version
    @Column(
            name = "version",
            nullable = false
    )
    private long version;


    // =====================================================
    // INBOX LIFECYCLE
    // =====================================================

    public void markProcessing() {

        if (status != InboxEventStatus.RECEIVED) {
            throw new IllegalStateException(
                    "Only RECEIVED inbox events can be processed."
            );
        }

        if (nextAttemptAt != null
                && nextAttemptAt.isAfter(Instant.now())) {

            throw new IllegalStateException(
                    "Inbox event is not ready for another processing attempt."
            );
        }

        this.status = InboxEventStatus.PROCESSING;
        this.attempts++;
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    public void markProcessed() {

        if (status != InboxEventStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Only PROCESSING inbox events can be marked as processed."
            );
        }

        this.status = InboxEventStatus.PROCESSED;
        this.processedAt = Instant.now();
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    /*
     * This method is called in a separate REQUIRES_NEW transaction
     * after a failed processing transaction has rolled back.
     *
     * Therefore, the attempt increment performed by markProcessing()
     * was rolled back. This method increments the persisted attempt
     * counter exactly once for the failed attempt.
     */
    public void recordFailure(
            String error,
            int maxAttempts,
            Instant nextAttemptAt
    ) {

        if (status == InboxEventStatus.PROCESSED
                || status == InboxEventStatus.FAILED) {

            return;
        }

        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "Maximum inbox processing attempts must be positive."
            );
        }

        this.attempts++;

        this.lastError = sanitizeError(error);

        if (attempts >= maxAttempts) {

            this.status = InboxEventStatus.FAILED;
            this.nextAttemptAt = null;

            return;
        }

        if (nextAttemptAt == null
                || !nextAttemptAt.isAfter(Instant.now())) {

            throw new IllegalArgumentException(
                    "Next inbox processing attempt must be in the future."
            );
        }

        this.status = InboxEventStatus.RECEIVED;
        this.nextAttemptAt = nextAttemptAt;
    }

    private String sanitizeError(String error) {

        if (error == null || error.isBlank()) {
            return "Unknown inbox processing failure.";
        }

        String sanitized = error
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();

        if (sanitized.length() > 150) {
            sanitized = sanitized.substring(0, 150);
        }

        return sanitized;
    }
}