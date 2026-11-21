package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findAllByUserIdOrderByCreatedAtDesc(
            UUID userId,
            Pageable pageable
    );

    Page<Notification> findAllByUserIdAndReadAtIsNullOrderByCreatedAtDesc(
            UUID userId,
            Pageable pageable
    );

    Page<Notification> findAllByUserIdAndArchivedAtIsNullOrderByCreatedAtDesc(
            UUID userId,
            Pageable pageable
    );

    long countByUserIdAndReadAtIsNullAndArchivedAtIsNull(
            UUID userId
    );

    boolean existsByUserIdAndSourceEventTypeAndSourceMessageId(
            UUID userId,
            String sourceEventType,
            String sourceMessageId
    );
}