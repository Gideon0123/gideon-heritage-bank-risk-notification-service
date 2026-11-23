package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.exception;

public class EncryptionOperationException extends RuntimeException {

    public EncryptionOperationException(String message) {
        super(message);
    }

    public EncryptionOperationException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
