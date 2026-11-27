package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.response.*;import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service.NotificationOutboxMonitoringService;import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.utils.TraceIdUtil;import jakarta.servlet.http.HttpServletRequest;import lombok.RequiredArgsConstructor;import org.springframework.http.ResponseEntity;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.web.bind.annotation.*;
@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/notifications/admin/operations/outbox")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class NotificationOutboxAdminController{

    private final NotificationOutboxMonitoringService service;

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationOutboxMonitoringSnapshot>> snapshot(
            HttpServletRequest r
    ){String id=r.getHeader("X-Request-Id");

        return ResponseEntity.ok(

                ApiResponse.success(
                        "Notification outbox monitoring snapshot retrieved.",
                        200,service.getSnapshot(),
                        r.getRequestURI(),
                        id == null || id.isBlank() ? TraceIdUtil.generate() : id));
    }
}
