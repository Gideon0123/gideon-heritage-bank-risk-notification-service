package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "notification.outbox")
public class NotificationOutboxProperties {

    private String exchange = "bank.notification-delivery.exchange";
    private String emailRoutingKey = "notification.delivery.email";
    private String pushRoutingKey = "notification.delivery.push";
    private String inAppRoutingKey = "notification.delivery.in-app";
    private int maxAttempts = 10;
    private int batchSize = 20;
    private long fixedDelay = 1000;
    private long leaseDurationSeconds = 60;
    private long publisherConfirmTimeoutSeconds = 10;

    // RETRY CONFIGURATION
    private Retry retry = new Retry();

    // RECOVERY CONFIGURATION
    private Recovery recovery = new Recovery();

    public String routingKeyFor(String channel) {

        return switch (channel) {
            case "EMAIL" -> emailRoutingKey;
            case "PUSH" -> pushRoutingKey;
            case "IN_APP" -> inAppRoutingKey;

            default -> throw new IllegalArgumentException(
                    "Unsupported notification delivery channel: " + channel
            );
        };
    }

    @Getter
    @Setter
    public static class Retry {

        private long initialDelaySeconds = 5;
        private double multiplier = 2.0;
        private long maxDelaySeconds = 3600;
        private double jitterFactor = 0.25;
    }

    @Getter
    @Setter
    public static class Recovery {

        private long fixedDelay = 30000;
        private int batchSize = 50;
    }
}