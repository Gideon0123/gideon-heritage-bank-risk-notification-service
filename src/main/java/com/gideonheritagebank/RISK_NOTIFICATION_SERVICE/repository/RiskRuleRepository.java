package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.RiskRule;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.RiskSignalType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;import java.util.Optional;import java.util.UUID;

public interface RiskRuleRepository extends JpaRepository<RiskRule,UUID>{

    List<RiskRule> findAllByOrderByCreatedAtDesc();
    List<RiskRule> findAllByActiveTrueOrderByWeightDesc();
    List<RiskRule> findAllBySignalTypeAndActiveTrue(RiskSignalType type);
    Optional<RiskRule> findByRuleKey(String key);
}
