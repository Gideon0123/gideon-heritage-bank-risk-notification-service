package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationTemplate;

public record RenderedNotificationTemplate(

        NotificationTemplate template,

        String subject,

        String title,

        String body

) {
}
