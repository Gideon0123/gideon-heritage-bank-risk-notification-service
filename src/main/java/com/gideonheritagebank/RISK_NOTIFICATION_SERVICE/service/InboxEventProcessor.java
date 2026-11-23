package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config.NotificationInboxProcessingProperties;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.InboxEventStatus;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository.InboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class InboxEventProcessor {

    private final InboxEventRepository inboxEventRepository;
    private final InboxEventProcessingTransaction processingTransaction;
    private final InboxEventFailureService failureService;
    private final NotificationInboxProcessingProperties properties;

    @Scheduled(
            fixedDelayString =
                    "${notification.inbox.processing.fixed-delay:1000}"
    )
    public void processReceivedEvents() {

        properties.validate();

        List<UUID> eventIds = inboxEventRepository.findProcessableIds(
                InboxEventStatus.RECEIVED,
                Instant.now(),
                PageRequest.of(
                        0,
                        properties.getBatchSize()
                )
        );

        if (eventIds.isEmpty()) {
            return;
        }

        log.debug(
                "Inbox processor selected {} Identity events.",
                eventIds.size()
        );

        for (UUID eventId : eventIds) {

            try {

                processingTransaction.process(eventId);

            } catch (Exception exception) {

                log.error(
                        "Identity inbox event processing failed. inboxEventId={}",
                        eventId,
                        exception
                );

                try {

                    failureService.recordFailure(
                            eventId,
                            exception
                    );

                } catch (Exception failureException) {

                    log.error(
                            "Could not persist Identity inbox processing failure. "
                                    + "inboxEventId={}",
                            eventId,
                            failureException
                    );
                }
            }
        }
    }
}