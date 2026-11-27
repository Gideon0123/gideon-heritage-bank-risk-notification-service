package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.RiskDecisionOutbox;import org.springframework.data.jpa.repository.JpaRepository;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.RiskOutboxStatus;
import java.util.Optional;import java.util.UUID;
public interface RiskDecisionOutboxRepository extends JpaRepository<RiskDecisionOutbox,UUID>,RiskDecisionOutboxRepositoryCustom { long countByStatus(RiskOutboxStatus status); Optional<RiskDecisionOutbox> findFirstByStatusOrderByCreatedAtAsc(RiskOutboxStatus status); }
