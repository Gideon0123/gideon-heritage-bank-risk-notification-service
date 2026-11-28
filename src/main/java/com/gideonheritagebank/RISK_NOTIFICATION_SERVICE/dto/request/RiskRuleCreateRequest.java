package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.request;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.*;
import jakarta.validation.constraints.*;import java.math.BigDecimal;

public record RiskRuleCreateRequest(
        @NotBlank
        @Size(max=100)
        String ruleKey,

        @NotBlank
        @Size(max=160)
        String name,

        @Size(max=1000)
        String description,

        @NotNull
        RiskSignalType signalType,

        @NotNull
        RiskRuleOperator operator,

        BigDecimal threshold,

        @Min(1)
        @Max(100)
        int weight,

        @NotNull
        RiskLevel severity,

        @NotNull
        RiskDecision decision
) {}
