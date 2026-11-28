package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.response;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationPreference;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationCategory;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationChannel;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record NotificationPreferenceResponse(

        UUID id, NotificationCategory category,
        NotificationChannel channel,
        boolean enabled,
        boolean quietHoursEnabled,
        LocalTime quietHoursStart,
        LocalTime quietHoursEnd,
        String timeZone,
        Instant createdAt,
        Instant updatedAt
) {
    public static NotificationPreferenceResponse from(NotificationPreference preference) {

        return new NotificationPreferenceResponse(
                preference.getId(),
                preference.getCategory(),
                preference.getChannel(),
                preference.isEnabled(),
                preference.isQuietHoursEnabled(),
                preference.getQuietHoursStart(),
                preference.getQuietHoursEnd(),
                preference.getTimeZone(),
                preference.getCreatedAt(),
                preference.getUpdatedAt()
        );
    }
}