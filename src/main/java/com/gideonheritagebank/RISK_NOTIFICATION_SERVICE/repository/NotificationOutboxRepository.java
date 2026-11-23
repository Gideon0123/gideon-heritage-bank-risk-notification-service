
package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationOutbox;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationOutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface NotificationOutboxRepository
        extends JpaRepository<NotificationOutbox, UUID>,
        NotificationOutboxRepositoryCustom {

    long countByStatus(NotificationOutboxStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT o
            FROM NotificationOutbox o
            WHERE o.id = :id
            """)
    Optional<NotificationOutbox> findByIdForUpdate(
            @Param("id") UUID id
    );

    Optional<NotificationOutbox> findFirstByStatusInOrderByCreatedAtAsc(
            Collection<NotificationOutboxStatus> statuses
    );
}