package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.controller;

import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.response.ApiResponse;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.response.NotificationResponse;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.dto.response.PagedResponse;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.service.NotificationInboxService;
import com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.utils.TraceIdUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationInboxService notificationInboxService;

    /* GET NOTIFICATION INBOX */
    @GetMapping
    @Operation(summary = "Retrieve the authenticated user's notification inbox.")
    public ResponseEntity<ApiResponse<PagedResponse<NotificationResponse>>> getInbox(

            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "false") boolean archived,
            HttpServletRequest request

    ) {

        PagedResponse<NotificationResponse> response = notificationInboxService.getInbox(
                page,
                size,
                sortBy,
                direction,
                unreadOnly,
                archived
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Notification inbox retrieved successfully.",
                        200,
                        response,
                        request.getRequestURI(),
                        resolveTraceId(request)
                )
        );
    }

    /* GET ONE NOTIFICATION */
    @GetMapping("/{notificationId}")
    @Operation(summary = "Retrieve one notification belonging to the authenticated user.")
    public ResponseEntity<ApiResponse<NotificationResponse>> getNotification(

            @PathVariable UUID notificationId,
            HttpServletRequest request

    ) {

        NotificationResponse response = notificationInboxService.getNotification(notificationId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Notification retrieved successfully.",
                        200,
                        response,
                        request.getRequestURI(),
                        resolveTraceId(request)
                )
        );
    }

    /* GET UNREAD COUNT */
    @GetMapping("/unread/count")
    @Operation(summary = "Get the authenticated user's unread notification count.")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(

            HttpServletRequest request
    ) {

        long unreadCount = notificationInboxService.getUnreadCount();

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Unread notification count retrieved successfully.",
                        200,
                        Map.of("unreadCount", unreadCount),
                        request.getRequestURI(),
                        resolveTraceId(request)
                )
        );
    }

    /* MARK ONE NOTIFICATION AS READ */
    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "Mark one notification as read.")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(

            @PathVariable UUID notificationId,
            HttpServletRequest request
    ) {

        NotificationResponse response = notificationInboxService.markAsRead(notificationId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Notification marked as read.",
                        200,
                        response,
                        request.getRequestURI(),
                        resolveTraceId(request)
                )
        );
    }

    /* MARK ALL NOTIFICATIONS AS READ */
    @PatchMapping("/read-all")
    @Operation(summary = "Mark all active unread notifications as read.")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllAsRead(

            HttpServletRequest request
    ) {

        int updatedCount = notificationInboxService.markAllAsRead();

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Unread notifications marked as read.",
                        200,
                        Map.of("updatedCount", updatedCount),
                        request.getRequestURI(),
                        resolveTraceId(request)
                )
        );
    }

    /* ARCHIVE NOTIFICATION */
    @PatchMapping("/{notificationId}/archive")
    @Operation(summary = "Archive a notification.")
    public ResponseEntity<ApiResponse<NotificationResponse>> archive(

            @PathVariable UUID notificationId,
            HttpServletRequest request

    ) {

        NotificationResponse response = notificationInboxService.archive(notificationId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Notification archived successfully.",
                        200,
                        response,
                        request.getRequestURI(),
                        resolveTraceId(request)
                )
        );
    }

    /* RESTORE NOTIFICATION */
    @PatchMapping("/{notificationId}/restore")
    @Operation(summary = "Restore an archived notification.")
    public ResponseEntity<ApiResponse<NotificationResponse>> restore(

            @PathVariable UUID notificationId,
            HttpServletRequest request
    ) {
        NotificationResponse response = notificationInboxService.restore(notificationId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Notification restored successfully.",
                        200,
                        response,
                        request.getRequestURI(),
                        resolveTraceId(request)
                )
        );
    }

    private String resolveTraceId(HttpServletRequest request) {

        String traceId = request.getHeader("X-Request-Id");

        if (traceId == null || traceId.isBlank()) {
            traceId = TraceIdUtil.generate();
        }

        return traceId;
    }
}
