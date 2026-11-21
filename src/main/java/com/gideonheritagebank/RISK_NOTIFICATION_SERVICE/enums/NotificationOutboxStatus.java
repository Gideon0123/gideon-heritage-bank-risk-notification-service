
package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums;

public enum NotificationOutboxStatus {

    PENDING,

    PROCESSING,

    RETRY_SCHEDULED,

    PUBLISHED,

    DEAD_LETTERED
}