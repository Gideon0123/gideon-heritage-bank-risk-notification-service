package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.response.NotificationOutboxMonitoringResponse;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.NotificationOutbox;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationOutboxStatus;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class NotificationOutboxMonitoringService {

    private final NotificationOutboxRepository outboxRepository;

    public NotificationOutboxMonitoringResponse getMonitoringSummary() {

        long pending = count(NotificationOutboxStatus.PENDING);

        long processing = count(NotificationOutboxStatus.PROCESSING);

        long retryScheduled = count(NotificationOutboxStatus.RETRY_SCHEDULED);

        long published = count(NotificationOutboxStatus.PUBLISHED);

        long deadLettered = count(NotificationOutboxStatus.DEAD_LETTERED);

        long total = pending
                + processing
                + retryScheduled
                + published
                + deadLettered;

        Instant oldestPending = findOldestCreatedAt(
                NotificationOutboxStatus.PENDING
        );

        Instant oldestRetry = findOldestCreatedAt(
                NotificationOutboxStatus.RETRY_SCHEDULED
        );

        Instant oldestProcessing = findOldestCreatedAt(
                NotificationOutboxStatus.PROCESSING
        );

        Instant oldestDeadLetter = findOldestCreatedAt(
                NotificationOutboxStatus.DEAD_LETTERED
        );

        return new NotificationOutboxMonitoringResponse(
                total,
                pending,
                processing,
                retryScheduled,
                published,
                deadLettered,
                oldestPending,
                oldestRetry,
                oldestProcessing,
                oldestDeadLetter,
                Instant.now()
        );
    }

    private long count(NotificationOutboxStatus status) {

        return outboxRepository.countByStatus(status);
    }

    private Instant findOldestCreatedAt(
            NotificationOutboxStatus status
    ) {
        return outboxRepository
                .findFirstByStatusOrderByCreatedAtAsc(status)
                .map(NotificationOutbox::getCreatedAt)
                .orElse(null);
    }
}