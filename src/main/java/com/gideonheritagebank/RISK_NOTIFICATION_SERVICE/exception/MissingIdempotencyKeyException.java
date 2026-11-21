package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.exception;

public class MissingIdempotencyKeyException extends RuntimeException {
    public MissingIdempotencyKeyException(String message) {
        super(message);
    }
}
