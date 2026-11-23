package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.exception.BadRequest;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "notification.email")
public class NotificationEmailProperties {

    /* Sender identity */
    private String from;
    private String fromName = "Gideon Heritage Bank";
    private String replyTo;
    private String provider = "SMTP";

    /* RabbitMQ */
    private String queue = "notification.delivery.email";

    /* Delivery worker */
    private long leaseDurationSeconds = 120;
    private int retryBatchSize = 20;
    private long retryFixedDelayMillis = 5000;
    private long recoveryFixedDelayMillis = 10000;

    /* Retry policy  */
    private int maxAttempts = 5;
    private long initialRetryDelaySeconds = 10;
    private long maximumRetryDelaySeconds = 900;
    private double retryJitterFactor = 0.20;

    public void validate() {

        if (from == null || from.isBlank()) {
            throw new BadRequest(
                    "Notification email sender address is not configured."
            );
        }

        if (fromName == null || fromName.isBlank()) {
            throw new BadRequest(
                    "Notification email sender name is required."
            );
        }

        if (provider == null || provider.isBlank()) {
            throw new BadRequest(
                    "Notification email provider is required."
            );
        }

        if (queue == null || queue.isBlank()) {
            throw new BadRequest(
                    "Notification email queue is required."
            );
        }

        if (leaseDurationSeconds < 30) {
            throw new BadRequest(
                    "Email delivery lease must be at least 30 seconds."
            );
        }

        if (retryBatchSize < 1) {
            throw new BadRequest(
                    "Email retry batch size must be positive."
            );
        }

        if (retryFixedDelayMillis < 100) {
            throw new BadRequest(
                    "Email retry polling delay must be at least 100 milliseconds."
            );
        }

        if (recoveryFixedDelayMillis < 1000) {
            throw new BadRequest(
                    "Email lease recovery delay must be at least 1000 milliseconds."
            );
        }

        if (maxAttempts < 1) {
            throw new BadRequest(
                    "Email maximum attempts must be positive."
            );
        }

        if (initialRetryDelaySeconds < 1) {
            throw new BadRequest(
                    "Email initial retry delay must be positive."
            );
        }

        if (maximumRetryDelaySeconds < initialRetryDelaySeconds) {
            throw new BadRequest(
                    "Email maximum retry delay cannot be less than the initial delay."
            );
        }

        if (retryJitterFactor < 0 || retryJitterFactor > 1) {
            throw new BadRequest(
                    "Email retry jitter factor must be between 0 and 1."
            );
        }
    }
}
