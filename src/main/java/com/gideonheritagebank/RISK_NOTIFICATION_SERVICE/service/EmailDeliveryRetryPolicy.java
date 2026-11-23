package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config.NotificationEmailProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class EmailDeliveryRetryPolicy {

    private final NotificationEmailProperties properties;

    public Instant calculateNextAttemptAt(
            int attemptNumber,
            Instant now
    ) {

        properties.validate();

        if (attemptNumber < 1) {
            throw new IllegalArgumentException("Attempt number must be at least 1.");
        }

        long initialDelay = properties.getInitialRetryDelaySeconds();
        long maximumDelay = properties.getMaximumRetryDelaySeconds();

        long delay = initialDelay;

        for (int i = 1; i < attemptNumber; i++) {

            if (delay >= maximumDelay) {
                delay = maximumDelay;
                break;
            }

            if (delay > maximumDelay / 2) {
                delay = maximumDelay;
                break;
            }

            delay *= 2;
        }

        delay = Math.min(delay, maximumDelay);

        double jitter = properties.getRetryJitterFactor();

        if (jitter == 0) {
            return now.plusSeconds(delay);
        }

        double lowerBound = Math.max(
                1,
                delay * (1.0 - jitter)
        );

        double upperBound = Math.min(
                maximumDelay,
                delay * (1.0 + jitter)
        );

        double randomizedDelay;

        if (upperBound <= lowerBound) {
            randomizedDelay = lowerBound;
        } else {
            randomizedDelay = ThreadLocalRandom.current().nextDouble(
                    lowerBound,
                    upperBound
            );
        }

        long finalDelay = Math.clamp(
                maximumDelay,
                1,
                Math.round(randomizedDelay)
        );

        return now.plusSeconds(finalDelay);
    }
}
