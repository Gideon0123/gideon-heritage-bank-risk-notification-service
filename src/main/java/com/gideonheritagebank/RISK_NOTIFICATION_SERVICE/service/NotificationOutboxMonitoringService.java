package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config.NotificationOutboxProperties;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.response.NotificationOutboxMonitoringSnapshot;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationOutbox;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationOutboxStatus;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class NotificationOutboxMonitoringService {

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationOutboxProperties properties;

    public NotificationOutboxMonitoringSnapshot getSnapshot() {

        Instant now = Instant.now();

        Map<NotificationOutboxStatus, Long> counts =
                new EnumMap<>(NotificationOutboxStatus.class);

        long totalRecords = 0;

        for (NotificationOutboxStatus status :
                NotificationOutboxStatus.values()) {

            long count = outboxRepository.countByStatus(status);

            counts.put(status, count);
            totalRecords += count;
        }

        long pending = getCount(
                counts,
                NotificationOutboxStatus.PENDING
        );

        long processing = getCount(
                counts,
                NotificationOutboxStatus.PROCESSING
        );

        long retryScheduled = getCount(
                counts,
                NotificationOutboxStatus.RETRY_SCHEDULED
        );

        long published = getCount(
                counts,
                NotificationOutboxStatus.PUBLISHED
        );

        long deadLettered = getCount(
                counts,
                NotificationOutboxStatus.DEAD_LETTERED
        );

        /*
         * Oldest waiting record includes both:
         * PENDING and RETRY_SCHEDULED.
         */
        Optional<NotificationOutbox> oldestPending =
                outboxRepository.findFirstByStatusInOrderByCreatedAtAsc(
                        List.of(
                                NotificationOutboxStatus.PENDING,
                                NotificationOutboxStatus.RETRY_SCHEDULED
                        )
                );

        Instant oldestPendingCreatedAt = oldestPending
                .map(NotificationOutbox::getCreatedAt)
                .orElse(null);

        Long oldestPendingAgeSeconds = oldestPendingCreatedAt == null
                ? null
                : Math.max(
                0,
                Duration.between(
                        oldestPendingCreatedAt,
                        now
                ).getSeconds()
        );

        Instant oldestRetryCreatedAt = findOldestCreatedAt(
                NotificationOutboxStatus.RETRY_SCHEDULED
        );

        Instant oldestProcessingCreatedAt = findOldestCreatedAt(
                NotificationOutboxStatus.PROCESSING
        );

        Instant oldestDeadLetterCreatedAt = findOldestCreatedAt(
                NotificationOutboxStatus.DEAD_LETTERED
        );

        boolean backlogAlert = oldestPendingAgeSeconds != null
                && oldestPendingAgeSeconds >=
                properties.getMonitoringOldestPendingThresholdSeconds();

        if (backlogAlert) {

            log.warn(
                    "Notification outbox backlog threshold exceeded. "
                            + "oldestPendingAgeSeconds={}, pending={}, "
                            + "retryScheduled={}, processing={}, deadLettered={}",
                    oldestPendingAgeSeconds,
                    pending,
                    retryScheduled,
                    processing,
                    deadLettered
            );
        }

        if (deadLettered > 0) {

            log.error(
                    "Notification outbox contains dead-lettered records. count={}",
                    deadLettered
            );
        }

        return new NotificationOutboxMonitoringSnapshot(
                Collections.unmodifiableMap(counts),
                totalRecords,
                pending,
                processing,
                retryScheduled,
                published,
                deadLettered,
                oldestPendingCreatedAt,
                oldestPendingAgeSeconds,
                oldestRetryCreatedAt,
                oldestProcessingCreatedAt,
                oldestDeadLetterCreatedAt,
                backlogAlert,
                now
        );
    }

    public Map<NotificationOutboxStatus, Long> getStatusCounts() {

        Map<NotificationOutboxStatus, Long> counts =
                new EnumMap<>(NotificationOutboxStatus.class);

        for (NotificationOutboxStatus status :
                NotificationOutboxStatus.values()) {

            counts.put(
                    status,
                    outboxRepository.countByStatus(status)
            );
        }

        return Collections.unmodifiableMap(counts);
    }

    public long getDeadLetteredCount() {

        return outboxRepository.countByStatus(
                NotificationOutboxStatus.DEAD_LETTERED
        );
    }

    public long getPublishedCount() {

        return outboxRepository.countByStatus(
                NotificationOutboxStatus.PUBLISHED
        );
    }

    private Instant findOldestCreatedAt(
            NotificationOutboxStatus status
    ) {

        return outboxRepository
                .findFirstByStatusOrderByCreatedAtAsc(status)
                .map(NotificationOutbox::getCreatedAt)
                .orElse(null);
    }

    private long getCount(
            Map<NotificationOutboxStatus, Long> counts,
            NotificationOutboxStatus status
    ) {
        return counts.getOrDefault(status, 0L);
    }
}