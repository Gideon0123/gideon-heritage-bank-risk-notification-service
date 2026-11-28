package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.controller;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.request.RiskRuleCreateRequest;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.request.RiskRuleUpdateRequest;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.response.ApiResponse;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.response.RiskRuleResponse;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service.RiskRuleManagementService;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.utils.TraceIdUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/risk/admin/rules")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class RiskRuleAdminController {

    private final RiskRuleManagementService ruleService;

    @GetMapping
    @Operation(summary = "List configurable risk rules.")
    public ResponseEntity<ApiResponse<List<RiskRuleResponse>>> getAll(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Risk rules retrieved.", 200, ruleService.getAll(),
                request.getRequestURI(), traceId(request)
        ));
    }

    @PostMapping
    @Operation(summary = "Create a configurable risk rule.")
    public ResponseEntity<ApiResponse<RiskRuleResponse>> create(
            @Valid @RequestBody RiskRuleCreateRequest body,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Risk rule created.", 201, ruleService.create(body),
                request.getRequestURI(), traceId(request)
        ));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a configurable risk rule.")
    public ResponseEntity<ApiResponse<RiskRuleResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody RiskRuleUpdateRequest body,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Risk rule updated.", 200, ruleService.update(id, body),
                request.getRequestURI(), traceId(request)
        ));
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<RiskRuleResponse>> activate(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Risk rule activated.", 200, ruleService.activate(id),
                request.getRequestURI(), traceId(request)
        ));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<RiskRuleResponse>> deactivate(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Risk rule deactivated.", 200, ruleService.deactivate(id),
                request.getRequestURI(), traceId(request)
        ));
    }

    private String traceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Request-Id");
        return traceId == null || traceId.isBlank() ? TraceIdUtil.generate() : traceId;
    }
}
