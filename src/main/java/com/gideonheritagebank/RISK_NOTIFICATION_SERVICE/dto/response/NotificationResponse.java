package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.response;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.Notification;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationCategory;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A notification displayed in the authenticated user's inbox.")
public record NotificationResponse(

        @Schema(description = "Notification identifier.")
        UUID id,

        @Schema(description = "Notification type.")
        NotificationType notificationType,

        @Schema(description = "Notification category.")
        NotificationCategory category,

        @Schema(description = "Notification title.")
        String title,

        @Schema(description = "Notification body.")
        String body,

        @Schema(description = "Optional frontend navigation URL.")
        String actionUrl,

        @Schema(description = "Optional serialized notification metadata.")
        String metadataJson,

        @Schema(description = "When the notification was created.")
        Instant createdAt,

        @Schema(description = "When the notification expires.")
        Instant expiresAt,

        @Schema(description = "When the notification was read. Null means unread.")
        Instant readAt,

        @Schema(description = "When the notification was archived.")
        Instant archivedAt,

        @Schema(description = "Whether the notification has been read.")
        boolean read,

        @Schema(description = "Whether the notification is archived.")
        boolean archived,

        @Schema(description = "Whether the notification has expired.")
        boolean expired

) {

    public static NotificationResponse from(Notification notification) {

        return new NotificationResponse(
                notification.getId(),
                notification.getNotificationType(),
                notification.getCategory(),
                notification.getTitle(),
                notification.getBody(),
                notification.getActionUrl(),
                notification.getMetadataJson(),
                notification.getCreatedAt(),
                notification.getExpiresAt(),
                notification.getReadAt(),
                notification.getArchivedAt(),
                notification.isRead(),
                notification.isArchived(),
                notification.isExpired()
        );
    }
}
