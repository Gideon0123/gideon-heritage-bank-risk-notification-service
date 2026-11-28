package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record NotificationPreferenceUpdateRequest(

        @NotNull
        Boolean enabled,

        boolean quietHoursEnabled,

        LocalTime quietHoursStart,

        LocalTime quietHoursEnd,

        @NotBlank
        @Size(max = 64)
        String timeZone
) {}