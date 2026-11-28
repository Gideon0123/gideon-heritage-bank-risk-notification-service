package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.request.NotificationPreferenceUpdateRequest;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.response.ApiResponse;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.response.NotificationPreferenceResponse;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationCategory;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.enums.NotificationChannel;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service.NotificationPreferenceService;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.utils.TraceIdUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/notifications/preferences")
@RequiredArgsConstructor
public class NotificationPreferenceController {

    private final NotificationPreferenceService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationPreferenceResponse>>> getMine(
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Notification preferences retrieved.",
                        200,
                        service.getMine(),
                        request.getRequestURI(),
                        trace(request)
                )
        );
    }

    @PutMapping("/{category}/{channel}")
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> upsert(
            @PathVariable NotificationCategory category,
            @PathVariable NotificationChannel channel,
            @Valid @RequestBody NotificationPreferenceUpdateRequest body,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Notification preference updated.",
                        200,
                        service.upsert(category, channel, body),
                        request.getRequestURI(),
                        trace(request)
                )
        );
    }

    @PostMapping("/defaults")
    public ResponseEntity<ApiResponse<List<NotificationPreferenceResponse>>> initializeDefaults(
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Default notification preferences initialized.",
                        200,
                        service.initializeDefaults(),
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
