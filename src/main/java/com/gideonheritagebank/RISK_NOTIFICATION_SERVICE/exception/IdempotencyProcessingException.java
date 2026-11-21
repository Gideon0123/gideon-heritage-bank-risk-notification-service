package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.exception;

public class IdempotencyProcessingException extends RuntimeException {
    public IdempotencyProcessingException(String message) {
        super(message);
    }
}
