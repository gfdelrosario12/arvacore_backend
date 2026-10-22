package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.response.AlertResponse;
import com.aquaflow.backend.entity.Alert;
import com.aquaflow.backend.infrastructure.exception.ResourceNotFoundException;
import com.aquaflow.backend.infrastructure.util.DtoMapper;
import com.aquaflow.backend.persistence.AlertRepository;
import jakarta.persistence.criteria.Predicate;
import com.aquaflow.backend.infrastructure.realtime.RealtimeSseBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Transactional
public class AlertServiceImpl implements AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertServiceImpl.class);

    private final AlertRepository alertRepository;
    private final RealtimeSseBroadcaster sseBroadcaster;
    private final Map<String, LocalDateTime> dedupCooldownMap = new ConcurrentHashMap<>();

    @Autowired
    public AlertServiceImpl(AlertRepository alertRepository, @Autowired(required = false) RealtimeSseBroadcaster sseBroadcaster) {
        this.alertRepository = alertRepository;
        this.sseBroadcaster = sseBroadcaster;
    }

    public AlertServiceImpl(AlertRepository alertRepository) {
        this(alertRepository, null);
    }

    @Override
    public AlertResponse createAlert(String deviceId, Long zoneId, String alertType, String alertLevel, String message) {
        return createNotification(
                alertType,
                null,
                message,
                alertLevel,
                null,
                deviceId,
                null,
                zoneId,
                zoneId != null ? "Q" + zoneId : null,
                "SYSTEM",
                null,
                null,
                null,
                null,
                0,
                null
        );
    }

    @Override
    public AlertResponse createNotification(
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
    ) {
        LocalDateTime now = LocalDateTime.now();

        if (dedupKey != null && !dedupKey.isBlank() && cooldownSeconds > 0) {
            LocalDateTime cutoff = now.minusSeconds(cooldownSeconds);
            LocalDateTime cachedLast = dedupCooldownMap.get(dedupKey);
            if (cachedLast != null && cachedLast.isAfter(cutoff)) {
                log.debug("Notification suppressed by in-memory cooldown for dedupKey={}", dedupKey);
                return alertRepository.findFirstByDedupKeyAndCreatedAtAfterOrderByCreatedAtDesc(dedupKey, cutoff)
                        .map(DtoMapper::toAlertResponse)
                        .orElse(null);
            }
            Optional<Alert> recentDb = alertRepository.findFirstByDedupKeyAndCreatedAtAfterOrderByCreatedAtDesc(dedupKey, cutoff);
            if (recentDb.isPresent()) {
                dedupCooldownMap.put(dedupKey, recentDb.get().getCreatedAt());
                log.debug("Notification suppressed by DB cooldown for dedupKey={}", dedupKey);
                return DtoMapper.toAlertResponse(recentDb.get());
            }
            dedupCooldownMap.put(dedupKey, now);
        }

        String normalizedLevel = normalizeAlertLevel(severity);
        String normalizedCategory = (category != null && !category.isBlank()) ? category.toLowerCase(Locale.ROOT) : "hardware";
        String effectiveSource = (sourceName != null && !sourceName.isBlank())
                ? sourceName
                : (zoneCode != null && !zoneCode.isBlank()
                    ? "Monitoring Quadrant " + zoneCode
                    : (nodeId != null && !nodeId.isBlank()
                        ? "Node " + nodeId
                        : "Centralized Irrigation System"));
        String effectiveScope = (targetScope != null && !targetScope.isBlank())
                ? targetScope
                : (zoneCode != null && !zoneCode.isBlank()
                    ? zoneCode
                    : (nodeId != null && !nodeId.isBlank()
                        ? nodeId
                        : "ENTIRE FIELD"));
        String effectiveAction = (recommendedAction != null && !recommendedAction.isBlank())
                ? recommendedAction
                : defaultRecommendedAction(normalizedCategory, normalizedLevel, nodeId, zoneCode);

        Alert alert = Alert.builder()
                .deviceId(nodeId)
                .zoneId(zoneId)
                .fieldId(fieldId)
                .zoneCode(zoneCode)
                .alertType(eventType != null ? eventType : "SYSTEM_EVENT")
                .alertLevel(normalizedLevel)
                .title(title != null && !title.isBlank() ? title : formatTitle(eventType))
                .category(normalizedCategory)
                .message(message)
                .actorId(actorId != null ? actorId : "SYSTEM")
                .sourceName(effectiveSource)
                .targetScope(effectiveScope)
                .recommendedAction(effectiveAction)
                .dedupKey(dedupKey)
                .metadataJson(metadataJson)
                .acknowledged(false)
                .createdAt(now)
                .build();

        Alert saved = alertRepository.save(alert);
        log.info("Notification recorded [id={}, type={}, level={}, node={}]",
                saved != null ? saved.getId() : null, eventType, normalizedLevel, nodeId);

        AlertResponse resp = DtoMapper.toAlertResponse(saved != null ? saved : alert);
        if (sseBroadcaster != null && resp != null) {
            try {
                sseBroadcaster.broadcastNotificationCreated(resp);
            } catch (Exception e) {
                log.warn("Failed to broadcast SSE notification for {}: {}", resp.getId(), e.getMessage());
            }
        }
        return resp;
    }

    @Override
    public AlertResponse acknowledgeAlert(Long alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found with id: " + alertId));

        if (!alert.isAcknowledged()) {
            alert.setAcknowledged(true);
            alert.setAcknowledgedAt(LocalDateTime.now());
            alert = alertRepository.save(alert);
        }
        log.info("Alert acknowledged with id: {}", alertId);
        return DtoMapper.toAlertResponse(alert);
    }

    @Override
    public int markAllAsRead() {
        int updated = alertRepository.markAllAsRead(LocalDateTime.now());
        log.info("Marked {} notifications as read", updated);
        return updated;
    }

    @Override
    public void deleteAlert(Long alertId) {
        if (alertRepository.existsById(alertId)) {
            alertRepository.deleteById(alertId);
            log.info("Notification deleted with id: {}", alertId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount() {
        return alertRepository.countByAcknowledgedFalse();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getSyncSummary(Long sinceId, LocalDateTime since) {
        long unreadCount = alertRepository.countByAcknowledgedFalse();
        long totalCount = alertRepository.count();
        Optional<Alert> latest = alertRepository.findFirstByOrderByCreatedAtDesc();
        Long latestId = latest.map(Alert::getId).orElse(0L);
        LocalDateTime latestTimestamp = latest.map(Alert::getCreatedAt).orElse(null);

        List<AlertResponse> delta = getNotificationsIncremental(sinceId, since, 50);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unreadCount", unreadCount);
        result.put("totalCount", totalCount);
        result.put("latestId", latestId);
        result.put("latestTimestamp", latestTimestamp);
        result.put("hasNew", !delta.isEmpty());
        result.put("count", delta.size());
        result.put("items", delta);
        result.put("notifications", delta);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AlertResponse> queryNotifications(
            String category,
            String severity,
            Boolean unreadOnly,
            String nodeId,
            Long fieldId,
            Long zoneId,
            String search,
            Pageable pageable
    ) {
        Specification<Alert> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase(Locale.ROOT)));
            }
            if (severity != null && !severity.isBlank()) {
                String norm = normalizeAlertLevel(severity);
                predicates.add(cb.equal(cb.upper(root.get("alertLevel")), norm));
            }
            if (Boolean.TRUE.equals(unreadOnly)) {
                predicates.add(cb.isFalse(root.get("acknowledged")));
            }
            if (nodeId != null && !nodeId.isBlank()) {
                predicates.add(cb.equal(root.get("deviceId"), nodeId.trim()));
            }
            if (fieldId != null) {
                predicates.add(cb.equal(root.get("fieldId"), fieldId));
            }
            if (zoneId != null) {
                predicates.add(cb.equal(root.get("zoneId"), zoneId));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                Predicate pTitle = cb.like(cb.lower(root.get("title")), pattern);
                Predicate pMsg = cb.like(cb.lower(root.get("message")), pattern);
                Predicate pSrc = cb.like(cb.lower(root.get("sourceName")), pattern);
                Predicate pNode = cb.like(cb.lower(root.get("deviceId")), pattern);
                predicates.add(cb.or(pTitle, pMsg, pSrc, pNode));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable effectivePageable = pageable;
        if (pageable == null || pageable.isUnpaged()) {
            effectivePageable = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt"));
        } else if (pageable.getSort().isUnsorted()) {
            effectivePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        return alertRepository.findAll(spec, effectivePageable).map(DtoMapper::toAlertResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertResponse> getNotificationsIncremental(Long sinceId, LocalDateTime since, int limit) {
        int boundedLimit = Math.min(Math.max(limit, 1), 200);
        List<Alert> alerts;
        if (sinceId != null && sinceId > 0) {
            alerts = alertRepository.findByIdGreaterThanOrderByCreatedAtDesc(sinceId);
        } else if (since != null) {
            alerts = alertRepository.findByCreatedAtAfterOrderByCreatedAtDesc(since);
        } else {
            Pageable page = PageRequest.of(0, boundedLimit, Sort.by(Sort.Direction.DESC, "createdAt"));
            return alertRepository.findAll(page).map(DtoMapper::toAlertResponse).getContent();
        }
        return alerts.stream()
                .limit(boundedLimit)
                .map(DtoMapper::toAlertResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AlertResponse> getAlertsByZoneId(Long zoneId, Pageable pageable) {
        return alertRepository.findByZoneId(zoneId, pageable).map(DtoMapper::toAlertResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AlertResponse> getAlertsByDeviceId(String deviceId, Pageable pageable) {
        return alertRepository.findByDeviceId(deviceId, pageable).map(DtoMapper::toAlertResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertResponse> getUnacknowledgedAlerts() {
        return alertRepository.findByAcknowledgedFalseOrderByCreatedAtDesc().stream()
                .map(DtoMapper::toAlertResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertResponse> getAllAlerts() {
        List<Alert> list = alertRepository.findAllByOrderByCreatedAtDesc();
        if (list == null || list.isEmpty()) {
            list = alertRepository.findAll();
        }
        return list.stream()
                .map(DtoMapper::toAlertResponse)
                .collect(Collectors.toList());
    }

    private String normalizeAlertLevel(String severity) {
        if (severity == null || severity.isBlank()) return "INFO";
        String upper = severity.trim().toUpperCase(Locale.ROOT);
        if (upper.contains("CRIT") || upper.contains("ERR") || upper.contains("HIGH") || upper.contains("EMERG")) {
            return "CRITICAL";
        }
        if (upper.contains("WARN") || upper.contains("MED") || upper.contains("DEGRAD")) {
            return "WARNING";
        }
        return "INFO";
    }

    private String formatTitle(String eventType) {
        if (eventType == null || eventType.isBlank()) return "System Notification";
        String[] words = eventType.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private String defaultRecommendedAction(String category, String level, String nodeId, String zoneCode) {
        if ("irrigation".equalsIgnoreCase(category)) {
            return "Open Irrigation Control to inspect active schedules, valves, and field water delivery.";
        }
        if ("agronomic".equalsIgnoreCase(category)) {
            return "Inspect monitoring zone " + (zoneCode != null ? zoneCode : "readings") + " on the Field Dashboard.";
        }
        if ("connectivity".equalsIgnoreCase(category)) {
            return "Verify power and Wi-Fi/gateway connectivity for " + (nodeId != null ? "node " + nodeId : "field devices") + ".";
        }
        if ("CRITICAL".equalsIgnoreCase(level) || "WARNING".equalsIgnoreCase(level)) {
            return "Inspect physical sensor wiring and device diagnostics for " + (nodeId != null ? nodeId : "the affected node") + ".";
        }
        return "Review details in the ArvaLink dashboard.";
    }
}
