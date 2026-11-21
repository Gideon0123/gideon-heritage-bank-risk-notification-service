package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums;

public enum NotificationType {

    // Identity notifications
    EMAIL_VERIFICATION,

    PASSWORD_RESET,

    STAFF_ONBOARDING,

    CUSTOMER_CONVERTED_TO_STAFF,

    EMAIL_CHANGE_VERIFICATION,

    EMAIL_CHANGED,

    // Account notifications
    ACCOUNT_CREATED,

    ACCOUNT_UPDATED,

    ACCOUNT_RESTRICTED,

    ACCOUNT_CLOSED,

    // Transaction notifications
    TRANSACTION_SUCCESSFUL,

    TRANSACTION_FAILED,

    TRANSACTION_REVERSED,

    // Risk notifications
    SUSPICIOUS_ACTIVITY,

    RISK_ALERT,

    ACCOUNT_SECURITY_ALERT,

    // General notifications
    SYSTEM_ANNOUNCEMENT,

    GENERAL
}