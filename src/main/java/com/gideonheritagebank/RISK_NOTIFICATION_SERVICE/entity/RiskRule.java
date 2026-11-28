package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.RiskDecision;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.RiskLevel;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.RiskRuleOperator;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.RiskSignalType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "risk_rules",
        uniqueConstraints = @UniqueConstraint(name = "uq_risk_rule_key", columnNames = "rule_key"),
        indexes = @Index(name = "idx_risk_rules_active_signal", columnList = "active, signal_type")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiskRule {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "rule_key", nullable = false, length = 100)
    private String ruleKey;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false, length = 50)
    private RiskSignalType signalType;

    @Enumerated(EnumType.STRING)
    @Column(name = "comparison_operator", nullable = false, length = 40)
    private RiskRuleOperator operator;

    @Column(precision = 19, scale = 4)
    private BigDecimal threshold;

    @Column(nullable = false)
    private int weight;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskLevel severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskDecision decision;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static RiskRule create(
            String key,
            String name,
            String description,
            RiskSignalType signalType,
            RiskRuleOperator operator,
            BigDecimal threshold,
            int weight,
            RiskLevel severity,
            RiskDecision decision
    ) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw new IllegalArgumentException("Rule key is required and must not exceed 100 characters.");
        }
        if (name == null || name.isBlank() || name.length() > 160) {
            throw new IllegalArgumentException("Rule name is required and must not exceed 160 characters.");
        }
        if (signalType == null || operator == null || severity == null || decision == null) {
            throw new IllegalArgumentException("Rule type, operator, severity and decision are required.");
        }
        if (weight < 1 || weight > 100) {
            throw new IllegalArgumentException("Rule weight must be between 1 and 100.");
        }
        validateThreshold(operator, threshold);

        RiskRule rule = new RiskRule();
        rule.ruleKey = key.trim();
        rule.name = name.trim();
        rule.description = description;
        rule.signalType = signalType;
        rule.operator = operator;
        rule.threshold = threshold;
        rule.weight = weight;
        rule.severity = severity;
        rule.decision = decision;
        return rule;
    }

    public void update(
            String name,
            String description,
            RiskRuleOperator operator,
            BigDecimal threshold,
            int weight,
            RiskLevel severity,
            RiskDecision decision
    ) {
        if (name == null || name.isBlank() || operator == null || severity == null || decision == null) {
            throw new IllegalArgumentException("Invalid risk rule update.");
        }
        if (weight < 1 || weight > 100) {
            throw new IllegalArgumentException("Risk rule weight must be between 1 and 100.");
        }
        validateThreshold(operator, threshold);

        this.name = name.trim();
        this.description = description;
        this.operator = operator;
        this.threshold = threshold;
        this.weight = weight;
        this.severity = severity;
        this.decision = decision;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    private static void validateThreshold(RiskRuleOperator operator, BigDecimal threshold) {
        if (operator != RiskRuleOperator.EXISTS && threshold == null) {
            throw new IllegalArgumentException("A numeric threshold is required for this operator.");
        }
    }
}
