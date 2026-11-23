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
import java.util.*;

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

        /*
         * Initialize every status, including statuses that
         * currently have zero records.
         */
        for (NotificationOutboxStatus status : NotificationOutboxStatus.values()) {

            long count = outboxRepository.countByStatus(status);

            counts.put(status, count);

            totalRecords += count;
        }

        long pending = counts.getOrDefault(
                NotificationOutboxStatus.PENDING,
                0L
        );

        long processing = counts.getOrDefault(
                NotificationOutboxStatus.PROCESSING,
                0L
        );

        long retryScheduled = counts.getOrDefault(
                NotificationOutboxStatus.RETRY_SCHEDULED,
                0L
        );

        long published = counts.getOrDefault(
                NotificationOutboxStatus.PUBLISHED,
                0L
        );

        long deadLettered = counts.getOrDefault(
                NotificationOutboxStatus.DEAD_LETTERED,
                0L
        );

        /*
         * The oldest pending record is based on creation time.
         * This indicates how long work has been waiting in the
         * outbox, even if it is not yet eligible for retry.
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
                Duration.between(oldestPendingCreatedAt, now).getSeconds()
        );

        boolean backlogAlert = oldestPendingAgeSeconds != null
                && oldestPendingAgeSeconds >=
                properties.getMonitoringOldestPendingThresholdSeconds();

        if (backlogAlert) {

            log.warn(
                    "Notification outbox backlog threshold exceeded. "
                            + "oldestPendingAgeSeconds={}, pending={}, retryScheduled={}",
                    oldestPendingAgeSeconds,
                    pending,
                    retryScheduled
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
                backlogAlert,
                now
        );
    }


    public Map<NotificationOutboxStatus, Long> getStatusCounts() {

        Map<NotificationOutboxStatus, Long> counts =
                new EnumMap<>(NotificationOutboxStatus.class);

        for (NotificationOutboxStatus status : NotificationOutboxStatus.values()) {

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
}