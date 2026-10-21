package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.response.AlertResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface AlertService {
    AlertResponse createAlert(String deviceId, Long zoneId, String alertType, String alertLevel, String message);

    AlertResponse createNotification(
            String eventType,
            String title,
            String message,
            String severity,
            String category,
            String nodeId,
            Long fieldId,
            Long zoneId,
            String zoneCode,
            String actorId,
            String sourceName,
            String targetScope,
            String recommendedAction,
            String dedupKey,
            int cooldownSeconds,
            String metadataJson
    );

    AlertResponse acknowledgeAlert(Long alertId);
    int markAllAsRead();
    void deleteAlert(Long alertId);
    long getUnreadCount();
    Map<String, Object> getSyncSummary(Long sinceId, LocalDateTime since);
    Page<AlertResponse> queryNotifications(
            String category,
            String severity,
            Boolean unreadOnly,
            String nodeId,
            Long fieldId,
            Long zoneId,
            String search,
            Pageable pageable
    );
    List<AlertResponse> getNotificationsIncremental(Long sinceId, LocalDateTime since, int limit);
    Page<AlertResponse> getAlertsByZoneId(Long zoneId, Pageable pageable);
    Page<AlertResponse> getAlertsByDeviceId(String deviceId, Pageable pageable);
    List<AlertResponse> getUnacknowledgedAlerts();
    List<AlertResponse> getAllAlerts();
}
