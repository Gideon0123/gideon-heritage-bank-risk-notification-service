package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.exception;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
