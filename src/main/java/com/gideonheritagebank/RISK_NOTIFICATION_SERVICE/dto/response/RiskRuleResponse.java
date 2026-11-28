package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.response;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.RiskRule;import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.*;import java.math.BigDecimal;import java.time.Instant;import java.util.UUID;

public record RiskRuleResponse(
        UUID id,
        String ruleKey,
        String name,
        String description,
        RiskSignalType signalType,
        RiskRuleOperator operator,
        BigDecimal threshold,
        int weight,
        RiskLevel severity,
        RiskDecision decision,
        boolean active,
        Instant createdAt,
        Instant updatedAt
){
    public static RiskRuleResponse from(RiskRule r){
        return new RiskRuleResponse(
                r.getId(),
                r.getRuleKey(),
                r.getName(),
                r.getDescription(),
                r.getSignalType(),
                r.getOperator(),
                r.getThreshold(),
                r.getWeight(),
                r.getSeverity(),
                r.getDecision(),
                r.isActive(),
                r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
