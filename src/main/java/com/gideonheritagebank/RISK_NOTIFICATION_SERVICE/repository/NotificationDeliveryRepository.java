package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationDelivery;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationChannel;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationDeliveryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationDeliveryRepository
        extends JpaRepository<NotificationDelivery, UUID> {

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
            NotificationChannel channel
    );


    /*
     * Pessimistic lock for claiming or updating a delivery.
     *
     * Fetch the parent notification so we can validate that the
     * notification ID in the RabbitMQ event matches the delivery.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT delivery
            FROM NotificationDelivery delivery
            JOIN FETCH delivery.notification notification
            WHERE delivery.id = :deliveryId
            """)
    Optional<NotificationDelivery> findByIdForUpdate(
            @Param("deliveryId") UUID deliveryId
    );


    /*
     * Database-backed retry and safety-net scan.
     *
     * PENDING rows are included as well as due RETRY_SCHEDULED
     * rows. This means a delivery can still be processed if
     * its original RabbitMQ trigger was delayed or lost.
     */
    @Query("""
            SELECT delivery.id
            FROM NotificationDelivery delivery
            WHERE delivery.channel = :channel
              AND delivery.status IN :statuses
              AND (
                    delivery.nextAttemptAt IS NULL
                    OR delivery.nextAttemptAt <= :now
              )
            ORDER BY delivery.createdAt ASC
            """)
    List<UUID> findDueDeliveryIds(
            @Param("channel") NotificationChannel channel,
            @Param("statuses") List<NotificationDeliveryStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable
    );


    /*
     * Find PROCESSING email deliveries whose worker lease expired.
     */
    @Query("""
            SELECT delivery.id
            FROM NotificationDelivery delivery
            WHERE delivery.channel = :channel
              AND delivery.status = :status
              AND delivery.lockedUntil <= :now
            ORDER BY delivery.lockedUntil ASC
            """)
    List<UUID> findExpiredLeaseIds(
            @Param("channel") NotificationChannel channel,
            @Param("status") NotificationDeliveryStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );
}