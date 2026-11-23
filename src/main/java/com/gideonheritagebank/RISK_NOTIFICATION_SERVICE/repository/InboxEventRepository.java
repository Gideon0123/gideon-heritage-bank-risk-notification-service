package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.InboxEvent;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.InboxEventStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InboxEventRepository
        extends JpaRepository<InboxEvent, UUID> {

    /*
     * PostgreSQL ON CONFLICT protects inbox ingestion from duplicate
     * RabbitMQ deliveries, including concurrent application instances.
     *
     * 1 = inserted
     * 0 = already received
     */
    @Modifying
    @Query(
            value = """
                    INSERT INTO notification_inbox_events (
                        id,
                        event_type,
                        message_id,
                        user_id,
                        encrypted_payload,
                        status,
                        attempts,
                        received_at,
                        version
                    )
                    VALUES (
                        :id,
                        :eventType,
                        :messageId,
                        :userId,
                        :encryptedPayload,
                        'RECEIVED',
                        0,
                        :receivedAt,
                        0
                    )
                    ON CONFLICT (event_type, message_id)
                    DO NOTHING
                    """,
            nativeQuery = true
    )
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("eventType") String eventType,
            @Param("messageId") String messageId,
            @Param("userId") UUID userId,
            @Param("encryptedPayload") String encryptedPayload,
            @Param("receivedAt") Instant receivedAt
    );

    /*
     * Select IDs only. Each event is subsequently loaded and locked
     * inside its own processing transaction.
     */
    @Query("""
            SELECT event.id
            FROM InboxEvent event
            WHERE event.status = :status
              AND (
                    event.nextAttemptAt IS NULL
                    OR event.nextAttemptAt <= :now
              )
            ORDER BY event.receivedAt ASC
            """)
    List<UUID> findProcessableIds(
            @Param("status") InboxEventStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT event
            FROM InboxEvent event
            WHERE event.id = :id
            """)
    Optional<InboxEvent> findByIdForUpdate(
            @Param("id") UUID id
    );
}