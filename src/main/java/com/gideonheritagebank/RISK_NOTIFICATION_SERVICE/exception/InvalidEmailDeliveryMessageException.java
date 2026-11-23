package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.exception;

public class InvalidEmailDeliveryMessageException
        extends RuntimeException {

    public InvalidEmailDeliveryMessageException(String message) {
        super(message);
    }

    public InvalidEmailDeliveryMessageException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}