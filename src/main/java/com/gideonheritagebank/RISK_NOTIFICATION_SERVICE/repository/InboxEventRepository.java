package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.InboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface InboxEventRepository extends JpaRepository<InboxEvent, UUID> {

    /*
     * PostgreSQL ON CONFLICT makes deduplication safe even when
     * two application instances receive the same event concurrently.
     *
     * Returns:
     * 1 = a new inbox record was inserted
     * 0 = the event already existed
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
}