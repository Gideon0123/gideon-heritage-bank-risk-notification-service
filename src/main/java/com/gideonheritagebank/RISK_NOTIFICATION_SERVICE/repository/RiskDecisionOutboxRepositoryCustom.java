package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.RiskDecisionOutbox;

import java.time.Instant;
import java.util.List;

public interface RiskDecisionOutboxRepositoryCustom {
    List<RiskDecisionOutbox> claimAvailable(String worker, Instant now, Instant leaseExpiry, int batchSize);
    List<RiskDecisionOutbox> claimExpired(Instant now, int batchSize);
}