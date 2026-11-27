package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.RiskOutboxStatus;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository.RiskDecisionOutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class RiskOutboxMetricsConfiguration{

    private final MeterRegistry registry;
    private final RiskDecisionOutboxRepository repository;

    @PostConstruct
    public void register(){
        for(RiskOutboxStatus status : RiskOutboxStatus.values()){
            Gauge.builder("bank.risk.outbox.records",
                    repository,r->r.countByStatus(status))
                    .tag("status",status.name())
                    .description("Risk decision outbox records by lifecycle status")
                    .register(registry);
        }
    }
}
