package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.response.ApiResponse;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.response.NotificationDeliveryHistoryResponse;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.response.PagedResponse;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service.NotificationDeliveryHistoryService;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.utils.TraceIdUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/notifications/deliveries")
@RequiredArgsConstructor
public class NotificationDeliveryHistoryController {

    private final NotificationDeliveryHistoryService service;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<NotificationDeliveryHistoryResponse>>> getMine(
            @RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int size,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Notification delivery history retrieved.",
                        200,service.getMine(page,size),
                        request.getRequestURI(),
                        trace(request)
                )
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NotificationDeliveryHistoryResponse>> getMine(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Notification delivery status retrieved.",
                        200,service.getMine(id),
                        request.getRequestURI(),
                        trace(request)
                )
        );
    }

    private String trace(
            HttpServletRequest request
    ) {
        String id = request.getHeader("X-Request-Id");
        return id == null || id.isBlank() ? TraceIdUtil.generate() : id;
    }
}
