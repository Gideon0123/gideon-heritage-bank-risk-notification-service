package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationOutboxStatus;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository.NotificationOutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

@Configuration @RequiredArgsConstructor
public class NotificationOutboxMetricsConfiguration {
    private final MeterRegistry meterRegistry;
    private final NotificationOutboxRepository repository;

    @PostConstruct
    public void registerGauges() {
        for (NotificationOutboxStatus status : NotificationOutboxStatus.values()) {
            Gauge.builder("bank.notification.outbox.records", repository, repo -> repo.countByStatus(status))
                    .tag("status", status.name())
                    .description("Notification outbox records by lifecycle status")
                    .register(meterRegistry);
        }
    }
}