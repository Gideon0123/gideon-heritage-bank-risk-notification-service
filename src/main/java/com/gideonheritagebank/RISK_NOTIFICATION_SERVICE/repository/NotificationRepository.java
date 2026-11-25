
package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository
        extends JpaRepository<Notification, UUID> {

    /*
     * Paginated notification inbox.
     *
     * archived = false:
     *      Return active inbox notifications.
     *
     * archived = true:
     *      Return archived notifications.
     *
     * unreadOnly = true:
     *      Return only notifications whose readAt is NULL.
     *
     * Expired notifications are excluded from the active inbox,
     * but remain accessible in the archived view if archived.
     */
    @Query("""
            SELECT notification
            FROM Notification notification
            WHERE notification.userId = :userId
              AND (
                    (:archived = TRUE AND notification.archivedAt IS NOT NULL)
                    OR
                    (:archived = FALSE AND notification.archivedAt IS NULL)
              )
              AND (
                    :unreadOnly = FALSE
                    OR notification.readAt IS NULL
              )
              AND (
                    :archived = TRUE
                    OR notification.expiresAt IS NULL
                    OR notification.expiresAt > :now
              )
            """)
    Page<Notification> findInbox(
            @Param("userId") UUID userId,
            @Param("archived") boolean archived,
            @Param("unreadOnly") boolean unreadOnly,
            @Param("now") Instant now,
            Pageable pageable
    );

    /*
     * Retrieve one notification belonging to a particular user.
     *
     * This prevents IDOR vulnerabilities.
     * A user cannot retrieve another user's notification
     * simply by changing the notification UUID.
     */
    Optional<Notification> findByIdAndUserId(
            UUID notificationId,
            UUID userId
    );

    /*
     * Count unread notifications in the active inbox.
     *
     * Archived and expired notifications are not counted.
     */
    @Query("""
            SELECT COUNT(notification)
            FROM Notification notification
            WHERE notification.userId = :userId
              AND notification.readAt IS NULL
              AND notification.archivedAt IS NULL
              AND (
                    notification.expiresAt IS NULL
                    OR notification.expiresAt > :now
              )
            """)
    long countUnreadNotifications(
            @Param("userId") UUID userId,
            @Param("now") Instant now
    );

    /*
     * Mark all active, unread notifications as read.
     *
     * This is a single database update instead of loading
     * every notification into application memory.
     *
     * We increment the version explicitly because JPQL bulk
     * updates bypass normal Hibernate @Version handling.
     */
    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
            UPDATE Notification notification
            SET notification.readAt = :now,
                notification.version = notification.version + 1
            WHERE notification.userId = :userId
              AND notification.readAt IS NULL
              AND notification.archivedAt IS NULL
              AND (
                    notification.expiresAt IS NULL
                    OR notification.expiresAt > :now
              )
            """)
    int markAllUnreadAsRead(
            @Param("userId") UUID userId,
            @Param("now") Instant now
    );

    /*
     * Existing queries retained.
     */

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
