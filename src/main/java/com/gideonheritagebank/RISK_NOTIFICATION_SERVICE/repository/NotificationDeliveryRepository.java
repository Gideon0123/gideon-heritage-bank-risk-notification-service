package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationDelivery;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {

    List<NotificationDelivery> findAllByNotificationIdOrderByCreatedAtAsc(
            UUID notificationId
    );

    Optional<NotificationDelivery> findByIdempotencyKey(
            String idempotencyKey
    );

    List<NotificationDelivery> findAllByStatus(
            NotificationDeliveryStatus status
    );

    long countByStatus(
            NotificationDeliveryStatus status
    );

    boolean existsByNotificationIdAndChannel(
            UUID notificationId,
            com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationChannel channel
    );
}