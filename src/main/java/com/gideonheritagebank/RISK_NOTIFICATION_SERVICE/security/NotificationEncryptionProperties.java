package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "notification.encryption")
public class NotificationEncryptionProperties {

    private String key;
}