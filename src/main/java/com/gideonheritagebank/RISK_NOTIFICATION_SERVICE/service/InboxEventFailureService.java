package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config.NotificationInboxProcessingProperties;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.InboxEvent;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.InboxEventStatus;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository.InboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InboxEventFailureService {

    private final InboxEventRepository inboxEventRepository;
    private final NotificationInboxProcessingProperties properties;

    /*
     * This method must execute after the original processing
     * transaction has rolled back.
     *
     * REQUIRES_NEW ensures that the failure counter and retry
     * timestamp are persisted independently.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            UUID inboxEventId,
            Throwable failure
    ) {

        InboxEvent inboxEvent = inboxEventRepository
                .findByIdForUpdate(inboxEventId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Inbox event not found: " + inboxEventId
                ));

        if (inboxEvent.getStatus() == InboxEventStatus.PROCESSED
                || inboxEvent.getStatus() == InboxEventStatus.FAILED) {
            return;
        }

        properties.validate();

        /*
         * The failed processing transaction rolled back its
         * markProcessing() attempt increment. Therefore the persisted
         * attempt count still represents the previous attempt.
         */
        int nextAttemptNumber = inboxEvent.getAttempts() + 1;

        String safeError = failure == null
                ? "Unknown inbox processing failure."
                : "Processing failed: "
                + failure.getClass().getSimpleName();

        if (nextAttemptNumber >= properties.getMaxAttempts()) {

            inboxEvent.recordFailure(
                    safeError,
                    properties.getMaxAttempts(),
                    null
            );

            log.error(
                    "Identity inbox event moved to FAILED after exhausting attempts. "
                            + "inboxEventId={}, attempts={}",
                    inboxEventId,
                    inboxEvent.getAttempts()
            );

            return;
        }

        Instant retryAt = calculateNextAttemptAt(
                nextAttemptNumber,
                Instant.now()
        );

        inboxEvent.recordFailure(
                safeError,
                properties.getMaxAttempts(),
                retryAt
        );

        log.warn(
                "Identity inbox event processing retry scheduled. "
                        + "inboxEventId={}, attempt={}, retryAt={}",
                inboxEventId,
                inboxEvent.getAttempts(),
                retryAt
        );
    }

    private Instant calculateNextAttemptAt(
            int attemptNumber,
            Instant now
    ) {

        long baseDelay = properties.getInitialRetryDelaySeconds();
        long maximumDelay = properties.getMaximumRetryDelaySeconds();

        for (int i = 1; i < attemptNumber; i++) {

            if (baseDelay >= maximumDelay) {
                baseDelay = maximumDelay;
                break;
            }

            if (baseDelay > maximumDelay / 2) {
                baseDelay = maximumDelay;
                break;
            }

            baseDelay *= 2;
        }

        baseDelay = Math.min(baseDelay, maximumDelay);

        double jitterFactor = 0.20;

        double minimumDelay = Math.max(
                1,
                baseDelay * (1.0 - jitterFactor)
        );

        double maximumJitteredDelay = Math.min(
                maximumDelay,
                baseDelay * (1.0 + jitterFactor)
        );

        double delay;

        if (maximumJitteredDelay <= minimumDelay) {
            delay = minimumDelay;
        } else {
            delay = ThreadLocalRandom.current().nextDouble(
                    minimumDelay,
                    maximumJitteredDelay
            );
        }

        long finalDelay = Math.clamp(Math.round(delay), 1,
                maximumDelay);

        return now.plusSeconds(finalDelay);
    }
}