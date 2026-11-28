package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.request.RiskRuleCreateRequest;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.request.RiskRuleUpdateRequest;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.response.RiskRuleResponse;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.entity.RiskRule;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.exception.ConflictException;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.exception.ResourceNotFoundException;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.repository.RiskRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RiskRuleManagementService {

    private final RiskRuleRepository repository;

    public List<RiskRuleResponse> getAll() {
        return repository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(RiskRuleResponse::from)
                .toList();
    }

    @Transactional
    public RiskRuleResponse create(RiskRuleCreateRequest request) {
        if (repository.findByRuleKey(request.ruleKey()).isPresent()) {
            throw new ConflictException("A risk rule with this key already exists.");
        }

        RiskRule rule = RiskRule.create(
                request.ruleKey(),
                request.name(),
                request.description(),
                request.signalType(),
                request.operator(),
                request.threshold(),
                request.weight(),
                request.severity(),
                request.decision()
        );

        return RiskRuleResponse.from(repository.save(rule));
    }

    @Transactional
    public RiskRuleResponse update(UUID id, RiskRuleUpdateRequest request) {
        RiskRule rule = find(id);
        rule.update(
                request.name(),
                request.description(),
                request.operator(),
                request.threshold(),
                request.weight(),
                request.severity(),
                request.decision()
        );
        return RiskRuleResponse.from(rule);
    }

    @Transactional
    public RiskRuleResponse activate(UUID id) {
        RiskRule rule = find(id);
        rule.activate();
        return RiskRuleResponse.from(rule);
    }

    @Transactional
    public RiskRuleResponse deactivate(UUID id) {
        RiskRule rule = find(id);
        rule.deactivate();
        return RiskRuleResponse.from(rule);
    }

    private RiskRule find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Risk rule was not found."));
    }
}
