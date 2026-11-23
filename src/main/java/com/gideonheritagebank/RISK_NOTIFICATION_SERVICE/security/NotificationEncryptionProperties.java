package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "notification.inbox")
public class NotificationEncryptionProperties {

    /*
     * Bound from notification.inbox.encryption-key.
     *
     * This is the existing NOTIFICATION_INBOX_ENCRYPTION_KEY.
     * We are deliberately not introducing another environment variable.
     */
    private String encryptionKey;
}