
package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NotificationOutboxProperties.class)
public class NotificationOutboxConfiguration {
}
