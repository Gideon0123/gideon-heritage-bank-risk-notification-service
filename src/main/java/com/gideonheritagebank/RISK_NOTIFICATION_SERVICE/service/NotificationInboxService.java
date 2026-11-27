package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.response.NotificationResponse;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.response.PagedResponse;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.CurrentUser;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.Notification;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.exception.ResourceNotFoundException;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationInboxService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final java.util.Set<String> ALLOWED_SORT_FIELDS =
            java.util.Set.of(
                    "createdAt",
                    "expiresAt",
                    "notificationType",
                    "category",
                    "title"
            );

    private final NotificationRepository notificationRepository;
    private final CurrentUserService currentUserService;
    private final ApplicationEventPublisher eventPublisher;

    /*
     * Retrieve the authenticated user's notification inbox.
     * Page numbering is 1-based at the API level.
     */
    public PagedResponse<NotificationResponse> getInbox(
            int page,
            int size,
            String sortBy,
            Sort.Direction direction,
            boolean unreadOnly,
            boolean archived
    ) {

        CurrentUser currentUser = currentUserService.getCurrentUser();

        validatePagination(page, size);
        validateSortField(sortBy);

        Pageable pageable = PageRequest.of(
                page - 1,
                size,
                Sort.by(direction, sortBy)
        );

        Page<Notification> notifications = notificationRepository.findInbox(
                currentUser.getId(),
                archived,
                unreadOnly,
                Instant.now(),
                pageable
        );

        Page<NotificationResponse> responsePage = notifications.map(NotificationResponse::from);

        return PagedResponse.from(responsePage);
    }


    public PagedResponse<NotificationResponse> getHistory(int page, int size) {
        CurrentUser currentUser = currentUserService.getCurrentUser();
        validatePagination(page, size);
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PagedResponse.from(notificationRepository
                .findAllByUserIdOrderByCreatedAtDesc(currentUser.getId(), pageable)
                .map(NotificationResponse::from));
    }

    /* Retrieve one notification belonging to the authenticated user. */
    public NotificationResponse getNotification(UUID notificationId) {

        CurrentUser currentUser = currentUserService.getCurrentUser();

        Notification notification = findUserNotification(
                notificationId,
                currentUser.getId()
        );

        return NotificationResponse.from(notification);
    }

    /*
     * Return the unread count for the authenticated user's
     * active inbox.
     */
    public long getUnreadCount() {

        CurrentUser currentUser = currentUserService.getCurrentUser();

        return notificationRepository.countUnreadNotifications(
                currentUser.getId(),
                Instant.now()
        );
    }

    /*
     * Mark a single notification as read.
     * This operation is idempotent:
     * calling it repeatedly does not change the original readAt.
     */
    @Transactional
    public NotificationResponse markAsRead(UUID notificationId) {

        CurrentUser currentUser = currentUserService.getCurrentUser();

        Notification notification = findUserNotification(
                notificationId,
                currentUser.getId()
        );

        notification.markAsRead();
        eventPublisher.publishEvent(new NotificationUnreadCountChangedEvent(currentUser.getId()));

        return NotificationResponse.from(notification);
    }


    @Transactional
    public NotificationResponse markAsUnread(UUID notificationId) {
        CurrentUser currentUser = currentUserService.getCurrentUser();
        Notification notification = findUserNotification(notificationId, currentUser.getId());
        notification.markAsUnread();
        eventPublisher.publishEvent(new NotificationUnreadCountChangedEvent(currentUser.getId()));
        return NotificationResponse.from(notification);
    }

    /*
     * Mark every active unread notification belonging to the
     * authenticated user as read.
     *
     * Returns the number of records updated.
     */
    @Transactional
    public int markAllAsRead() {

        CurrentUser currentUser = currentUserService.getCurrentUser();

        int updated = notificationRepository.markAllUnreadAsRead(
                currentUser.getId(),
                Instant.now()
        );
        if (updated > 0) eventPublisher.publishEvent(new NotificationUnreadCountChangedEvent(currentUser.getId()));
        return updated;
    }

    /*
     * Archive a notification.
     * Archiving does not delete the notification.
     */
    @Transactional
    public NotificationResponse archive(UUID notificationId) {

        CurrentUser currentUser = currentUserService.getCurrentUser();

        Notification notification = findUserNotification(
                notificationId,
                currentUser.getId()
        );

        notification.archive();
        eventPublisher.publishEvent(new NotificationUnreadCountChangedEvent(currentUser.getId()));

        return NotificationResponse.from(notification);
    }

    /* Restore an archived notification. */
    @Transactional
    public NotificationResponse restore(UUID notificationId) {

        CurrentUser currentUser = currentUserService.getCurrentUser();

        Notification notification = findUserNotification(
                notificationId,
                currentUser.getId()
        );

        notification.restore();
        eventPublisher.publishEvent(new NotificationUnreadCountChangedEvent(currentUser.getId()));

        return NotificationResponse.from(notification);
    }

    /*
     * Centralized ownership-aware lookup.
     *
     * We deliberately return the same 404 response whether:
     * - the notification does not exist, or
     * - the notification belongs to another user.
     *
     * This avoids revealing the existence of another user's
     * notification identifiers.
     */
    private Notification findUserNotification(
            UUID notificationId,
            UUID userId
    ) {

        if (notificationId == null) {
            throw new IllegalArgumentException("Notification ID is required.");
        }

        return notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification was not found."));
    }

    private void validatePagination(int page, int size) {

        if (page < 1) {
            throw new IllegalArgumentException("Page must be at least 1.");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and " + MAX_PAGE_SIZE + "."
            );
        }
    }

    private void validateSortField(String sortBy) {

        if (sortBy == null || !ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new IllegalArgumentException("Unsupported notification sort field.");
        }
    }
}
