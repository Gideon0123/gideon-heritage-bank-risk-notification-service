package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.outbox")
@Getter
@Setter
public class NotificationOutboxProperties {

    private String exchange = "bank.notification-delivery.exchange";

    private String emailRoutingKey = "notification.delivery.email";
    private String pushRoutingKey = "notification.delivery.push";
    private String inAppRoutingKey = "notification.delivery.in-app";

    // Publishing policy
    private int maxAttempts = 10;
    private int batchSize = 20;
    private long fixedDelay = 1000;
    private long leaseDurationSeconds = 60;
    private long publisherConfirmTimeoutSeconds = 10;

    // Retry policy
    private long initialRetryDelaySeconds = 5;
    private long maximumRetryDelaySeconds = 900;
    private double retryJitterFactor = 0.20;

    // Recovery policy
    private long recoveryFixedDelay = 5000;
    private int recoveryBatchSize = 50;

    // Monitoring
    private long monitoringOldestPendingThresholdSeconds = 300;

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

    public void validateRetryConfiguration() {

        if (maxAttempts < 1) {
            throw new IllegalStateException(
                    "Outbox maximum attempts must be at least 1."
            );
        }

        if (initialRetryDelaySeconds < 1) {
            throw new IllegalStateException(
                    "Initial retry delay must be at least 1 second."
            );
        }

        if (maximumRetryDelaySeconds < initialRetryDelaySeconds) {
            throw new IllegalStateException(
                    "Maximum retry delay cannot be less than initial retry delay."
            );
        }

        if (retryJitterFactor < 0 || retryJitterFactor > 1) {
            throw new IllegalStateException(
                    "Retry jitter factor must be between 0 and 1."
            );
        }
    }

    public void validateWorkerConfiguration() {

        validateRetryConfiguration();

        if (batchSize < 1) {
            throw new IllegalStateException(
                    "Outbox batch size must be at least 1."
            );
        }

        if (leaseDurationSeconds < 1) {
            throw new IllegalStateException(
                    "Outbox lease duration must be positive."
            );
        }

        if (publisherConfirmTimeoutSeconds < 1) {
            throw new IllegalStateException(
                    "Publisher confirmation timeout must be positive."
            );
        }

        if (recoveryBatchSize < 1) {
            throw new IllegalStateException(
                    "Recovery batch size must be positive."
            );
        }

        if (recoveryFixedDelay < 1) {
            throw new IllegalStateException(
                    "Recovery fixed delay must be positive."
            );
        }
    }
}