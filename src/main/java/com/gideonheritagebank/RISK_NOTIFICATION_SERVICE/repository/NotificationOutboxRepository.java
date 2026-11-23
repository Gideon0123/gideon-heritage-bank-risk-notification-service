
package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationOutbox;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationOutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationOutboxRepository
        extends JpaRepository<NotificationOutbox, UUID>,
        NotificationOutboxRepositoryCustom {

    long countByStatus(NotificationOutboxStatus status);

    Optional<NotificationOutbox> findFirstByStatusOrderByCreatedAtAsc(
            NotificationOutboxStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT o
            FROM NotificationOutbox o
            WHERE o.id = :id
            """)
    Optional<NotificationOutbox> findByIdForUpdate(
            @Param("id") UUID id
    );

    @Query(
            value = """
                    SELECT *
                    FROM notification_outbox
                    WHERE status = 'PROCESSING'
                      AND locked_until <= :now
                    ORDER BY locked_until ASC
                    LIMIT :batchSize
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true
    )
    List<NotificationOutbox> findExpiredLeasesForRecovery(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );
}