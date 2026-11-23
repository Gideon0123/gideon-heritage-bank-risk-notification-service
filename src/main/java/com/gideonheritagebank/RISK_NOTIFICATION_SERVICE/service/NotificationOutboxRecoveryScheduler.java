package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationOutboxRecoveryScheduler {

    private final NotificationOutboxRecoveryService recoveryService;

    @Scheduled(
            fixedDelayString = "${notification.outbox.recovery-fixed-delay:5000}"
    )
    public void runRecovery() {

        try {

            int processed = recoveryService.recoverExpiredLeases();

            if (processed > 0) {

                log.info(
                        "Outbox recovery scheduler processed {} expired records.",
                        processed
                );
            }

        } catch (Exception exception) {

            log.error(
                    "Notification outbox recovery scheduler failed.",
                    exception
            );
        }
    }
}