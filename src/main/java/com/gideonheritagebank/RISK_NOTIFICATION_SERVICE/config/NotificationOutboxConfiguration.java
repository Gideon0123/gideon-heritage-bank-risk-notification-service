package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.security.NotificationEncryptionProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        NotificationOutboxProperties.class,
        NotificationEncryptionProperties.class,
        NotificationInboxProcessingProperties.class
})
public class NotificationOutboxConfiguration {
}
