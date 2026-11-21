package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.exception;

public class NotificationOutboxPublishException extends RuntimeException {

    public NotificationOutboxPublishException(String message) {
        super(message);
    }

    public NotificationOutboxPublishException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
