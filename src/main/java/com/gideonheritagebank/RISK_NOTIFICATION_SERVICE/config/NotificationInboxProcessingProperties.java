package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "notification.inbox.processing")
public class NotificationInboxProcessingProperties {

    private long fixedDelay = 1000;

    private int batchSize = 20;

    private int maxAttempts = 5;

    private long initialRetryDelaySeconds = 5;

    private long maximumRetryDelaySeconds = 300;

    public void validate() {

        if (fixedDelay < 1) {
            throw new IllegalStateException(
                    "Inbox processing fixed delay must be positive."
            );
        }

        if (batchSize < 1) {
            throw new IllegalStateException(
                    "Inbox processing batch size must be positive."
            );
        }

        if (maxAttempts < 1) {
            throw new IllegalStateException(
                    "Inbox processing maximum attempts must be positive."
            );
        }

        if (initialRetryDelaySeconds < 1) {
            throw new IllegalStateException(
                    "Inbox retry delay must be positive."
            );
        }

        if (maximumRetryDelaySeconds < initialRetryDelaySeconds) {
            throw new IllegalStateException(
                    "Inbox maximum retry delay cannot be less than its initial delay."
            );
        }
    }
}
