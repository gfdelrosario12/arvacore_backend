package com.aquaflow.backend.api;

import com.aquaflow.backend.domain.AlertService;
import com.aquaflow.backend.dto.response.AlertResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/alerts", "/api/alerts", "/api/v1/notifications", "/api/notifications"})
public class AlertController {

    private static final Logger log = LoggerFactory.getLogger(AlertController.class);

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @PostMapping
    public ResponseEntity<AlertResponse> createAlert(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) Long zoneId,
            @RequestParam(defaultValue = "SYSTEM_ALERT") String alertType,
            @RequestParam(defaultValue = "INFO") String alertLevel,
            @RequestParam String message) {
        log.info("POST /api/v1/alerts");
        AlertResponse response = alertService.createAlert(deviceId, zoneId, alertType, alertLevel, message);
        return ResponseEntity.ok(response);
    }

    @RequestMapping(value = {"/{alertId}/acknowledge", "/{alertId}/read"}, method = {RequestMethod.PATCH, RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<AlertResponse> acknowledgeAlert(@PathVariable Long alertId) {
        log.info("Acknowledge/read notification {}", alertId);
        return ResponseEntity.ok(alertService.acknowledgeAlert(alertId));
    }

    @RequestMapping(value = {"/read-all", "/acknowledge-all"}, method = {RequestMethod.PUT, RequestMethod.POST, RequestMethod.PATCH})
    public ResponseEntity<Map<String, Object>> markAllAsRead() {
        log.info("Mark all notifications as read");
        int count = alertService.markAllAsRead();
        return ResponseEntity.ok(Map.of("markedReadCount", count, "unreadCount", 0));
    }

    @DeleteMapping("/{alertId}")
    public ResponseEntity<Void> deleteAlert(@PathVariable Long alertId) {
        log.info("DELETE notification {}", alertId);
        alertService.deleteAlert(alertId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount() {
        long count = alertService.getUnreadCount();
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    @GetMapping("/sync")
    public ResponseEntity<Map<String, Object>> getSyncSummary(
            @RequestParam(required = false) Long sinceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since
    ) {
        return ResponseEntity.ok(alertService.getSyncSummary(sinceId, since));
    }

    @GetMapping("/paged")
    public ResponseEntity<Page<AlertResponse>> getNotificationsPaged(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(required = false) String nodeId,
            @RequestParam(required = false) Long fieldId,
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) String search,
            Pageable pageable
    ) {
        return ResponseEntity.ok(alertService.queryNotifications(
                category, severity, unreadOnly, nodeId, fieldId, zoneId, search, pageable));
    }

    @GetMapping("/zone/{zoneId}")
    public ResponseEntity<Page<AlertResponse>> getAlertsByZoneId(@PathVariable Long zoneId, Pageable pageable) {
        log.info("GET /api/v1/alerts/zone/{}", zoneId);
        return ResponseEntity.ok(alertService.getAlertsByZoneId(zoneId, pageable));
    }

    @GetMapping("/device/{deviceId}")
    public ResponseEntity<Page<AlertResponse>> getAlertsByDeviceId(@PathVariable String deviceId, Pageable pageable) {
        log.info("GET /api/v1/alerts/device/{}", deviceId);
        return ResponseEntity.ok(alertService.getAlertsByDeviceId(deviceId, pageable));
    }

    @GetMapping("/unacknowledged")
    public ResponseEntity<List<AlertResponse>> getUnacknowledgedAlerts() {
        log.info("GET /api/v1/alerts/unacknowledged");
        return ResponseEntity.ok(alertService.getUnacknowledgedAlerts());
    }

    @GetMapping
    public ResponseEntity<List<AlertResponse>> getAllAlerts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(required = false) String nodeId,
            @RequestParam(required = false) Long fieldId,
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long sinceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since
    ) {
        if (sinceId != null || since != null) {
            return ResponseEntity.ok(alertService.getNotificationsIncremental(sinceId, since, 100));
        }
        if (category != null || severity != null || unreadOnly != null || nodeId != null || fieldId != null || zoneId != null || search != null) {
            return ResponseEntity.ok(alertService.queryNotifications(
                    category, severity, unreadOnly, nodeId, fieldId, zoneId, search, Pageable.unpaged()).getContent());
        }
        return ResponseEntity.ok(alertService.getAllAlerts());
    }
}
