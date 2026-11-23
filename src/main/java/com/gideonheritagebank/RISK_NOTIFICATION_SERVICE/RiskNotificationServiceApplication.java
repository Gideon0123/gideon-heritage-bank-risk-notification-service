package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config.NotificationOutboxProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(NotificationOutboxProperties.class)
public class RiskNotificationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(RiskNotificationServiceApplication.class, args);
	}

}
