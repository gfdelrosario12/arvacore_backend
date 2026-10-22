package com.aquaflow.backend.domain;

import com.aquaflow.backend.entity.*;
import com.aquaflow.backend.infrastructure.event.SystemEventPublisher;
import com.aquaflow.backend.persistence.AutoIrrigationConfigRepository;
import com.aquaflow.backend.persistence.FieldRepository;
import com.aquaflow.backend.persistence.IrrigationDecisionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional
public class ThresholdEvaluationServiceImpl implements ThresholdEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(ThresholdEvaluationServiceImpl.class);

    private final AutoIrrigationConfigRepository autoConfigRepository;
    private final FieldRepository fieldRepository;
    private final DownlinkQueueService downlinkQueueService;
    private final AlertService alertService;
    private final SystemEventPublisher systemEventPublisher;
    private final IrrigationDecisionRepository decisionRepository;
    private final ObjectMapper objectMapper;

    // Track last command type and execution time per node/field to enforce cooldown and deduplication
    private static final Map<String, LocalDateTime> lastCommandTimeMap = new ConcurrentHashMap<>();
    private static final Map<String, String> lastCommandTypeMap = new ConcurrentHashMap<>();

    // Track last notified sensor baseline values per node for significant-change detection
    private static final double SOIL_CHANGE_THRESHOLD_PCT = 10.0;
    private static final double WATER_LEVEL_CHANGE_THRESHOLD_CM = 10.0;
    private static final double SOIL_MOISTURE_CRITICAL = 20.0;
    private static final double SOIL_MOISTURE_RECOVERY = 30.0;
    private static final double ULTRASONIC_CRITICAL_CM = 10.0;
    private static final double ULTRASONIC_RECOVERY_CM = 15.0;
    private static final Map<String, Double> lastNotifiedSoilMap = new ConcurrentHashMap<>();
    private static final Map<String, Double> lastNotifiedWaterMap = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> nodeSoilCriticalState = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> nodeWaterCriticalState = new ConcurrentHashMap<>();

    @Autowired
    public ThresholdEvaluationServiceImpl(
            AutoIrrigationConfigRepository autoConfigRepository,
            FieldRepository fieldRepository,
            DownlinkQueueService downlinkQueueService,
            @Autowired(required = false) AlertService alertService,
            @Autowired(required = false) SystemEventPublisher systemEventPublisher,
            @Autowired(required = false) IrrigationDecisionRepository decisionRepository,
            @Autowired(required = false) ObjectMapper objectMapper) {
        this.autoConfigRepository = autoConfigRepository;
        this.fieldRepository = fieldRepository;
        this.downlinkQueueService = downlinkQueueService;
        this.alertService = alertService;
        this.systemEventPublisher = systemEventPublisher;
        this.decisionRepository = decisionRepository;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public void evaluateTelemetryThresholds(
            EdgeNode node,
            Double soilMoisture,
            Double waterLevelCm,
            String soilHealth,
            String ultrasonicHealth,
            String telemetryStatus) {

        if (node == null) return;

        Field field = null;
        if (node.getMonitoringZone() != null && node.getMonitoringZone().getField() != null) {
            field = node.getMonitoringZone().getField();
        } else {
            Optional<Field> defaultField = fieldRepository.findById(1L);
            if (defaultField.isPresent()) {
                field = defaultField.get();
            }
        }
        Long fieldId = field != null ? field.getId() : 1L;

        // 1. Fetch AutoIrrigationConfig for this field
        Optional<AutoIrrigationConfig> configOpt = autoConfigRepository.findByFieldId(fieldId);
        if (configOpt.isEmpty() && node.getId() != null) {
            configOpt = autoConfigRepository.findByEdgeNodeId(node.getId());
        }

        // Automatic control remains disabled until thresholds and configs exist and are enabled
        if (configOpt.isEmpty()) {
            return;
        }

        AutoIrrigationConfig config = configOpt.get();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return;
        }

        // Threshold parameters with defaults
        double minSoilMoisture = config.getMinSoilMoisturePercentage() != null ? config.getMinSoilMoisturePercentage() : 30.0;
        double maxSoilMoisture = config.getMaxSoilMoisturePercentage() != null ? config.getMaxSoilMoisturePercentage() : 70.0;
        double targetFloodDepth = config.getTargetFloodDepthCm() != null ? config.getTargetFloodDepthCm() : 5.0;
        int maxDurationMinutes = config.getMaxDurationMinutes() != null ? config.getMaxDurationMinutes() : 30;
        int minCooldownMinutes = config.getMinCooldownMinutes() != null ? config.getMinCooldownMinutes() : 5;

        // 2. Validate sensor health
        boolean isSoilHealthy = "OK".equalsIgnoreCase(soilHealth);
        boolean isUltrasonicHealthy = "OK".equalsIgnoreCase(ultrasonicHealth);

        // Requirement: Invalid readings transmit 0 for monitoring but must NEVER trigger START.
        // Sensor failure requires SAFE_STOP if active.
        if (!isSoilHealthy || !isUltrasonicHealthy) {
            log.warn("Sensor failure detected on node {} (soil={}, ultrasonic={}). Checking for SAFE_STOP.",
                    node.getNodeId(), soilHealth, ultrasonicHealth);

            String lastCmd = lastCommandTypeMap.get(node.getNodeId());
            if ("START_IRRIGATION".equals(lastCmd)) {
                dispatchCommand(node, fieldId, "SAFE_STOP",
                        "Sensor health degraded (soil=" + soilHealth + ", ultrasonic=" + ultrasonicHealth + ")",
                        "WARNING", 0);
            }
            return;
        }

        if (soilMoisture == null || waterLevelCm == null) {
            return;
        }

        // 2b. Significant-change notifications & Critical/Recovery transitions (only from healthy sensors, non-zero readings)
        if (alertService != null && soilMoisture > 0 && waterLevelCm >= 0) {
            String nodeKey = node.getNodeId();
            String zoneName = node.getMonitoringZone() != null ? node.getMonitoringZone().getCode() : nodeKey;

            // Soil critical / recovery transitions
            boolean wasSoilCritical = Boolean.TRUE.equals(nodeSoilCriticalState.get(nodeKey));
            if (!wasSoilCritical && soilMoisture <= SOIL_MOISTURE_CRITICAL) {
                nodeSoilCriticalState.put(nodeKey, true);
                alertService.createNotification(
                    "SOIL_CRITICAL",
                    "Soil Moisture Critical: " + String.format("%.1f%%", soilMoisture),
                    String.format("Critical soil dryness detected on %s (%s): %.1f%% (<= %.1f%%). Irrigation needed.",
                        nodeKey, zoneName, soilMoisture, SOIL_MOISTURE_CRITICAL),
                    "CRITICAL", "agronomic", nodeKey, fieldId,
                    node.getMonitoringZone() != null ? node.getMonitoringZone().getId() : null,
                    zoneName, "THRESHOLD_ENGINE", "ArvaCore Autonomous AWD Engine",
                    "ZONE", "Initiate irrigation reflood immediately.",
                    "SOIL_CRIT:" + nodeKey, 30, null
                );
                lastNotifiedSoilMap.put(nodeKey, soilMoisture);
            } else if (wasSoilCritical && soilMoisture >= SOIL_MOISTURE_RECOVERY) {
                nodeSoilCriticalState.put(nodeKey, false);
                alertService.createNotification(
                    "SOIL_RECOVERED",
                    "Soil Moisture Recovered: " + String.format("%.1f%%", soilMoisture),
                    String.format("Soil moisture on %s (%s) recovered to %.1f%% (>= %.1f%% safe threshold).",
                        nodeKey, zoneName, soilMoisture, SOIL_MOISTURE_RECOVERY),
                    "SUCCESS", "agronomic", nodeKey, fieldId,
                    node.getMonitoringZone() != null ? node.getMonitoringZone().getId() : null,
                    zoneName, "THRESHOLD_ENGINE", "ArvaCore Autonomous AWD Engine",
                    "ZONE", "Moisture restored.",
                    "SOIL_REC:" + nodeKey, 30, null
                );
                lastNotifiedSoilMap.put(nodeKey, soilMoisture);
            } else {
                Double prevSoil = lastNotifiedSoilMap.get(nodeKey);
                if (prevSoil != null) {
                    double soilDiff = soilMoisture - prevSoil;
                    if (Math.abs(soilDiff) >= SOIL_CHANGE_THRESHOLD_PCT) {
                        String dir = soilDiff > 0 ? "rose" : "dropped";
                        String sev = soilMoisture <= SOIL_MOISTURE_CRITICAL ? "CRITICAL" : (Math.abs(soilDiff) >= 15 ? "WARNING" : "INFO");
                        alertService.createNotification(
                            "SOIL_MOISTURE_CHANGE",
                            String.format("Soil Moisture %s by %.1f%%", dir, Math.abs(soilDiff)),
                            String.format("Soil moisture on node %s (%s) %s from %.1f%% to %.1f%% (Δ%.1f%%).",
                                nodeKey, zoneName, dir, prevSoil, soilMoisture, soilDiff),
                            sev, "agronomic", nodeKey, fieldId,
                            node.getMonitoringZone() != null ? node.getMonitoringZone().getId() : null,
                            zoneName, "THRESHOLD_ENGINE", "ArvaCore Threshold Monitor",
                            "ZONE", "Monitor soil moisture trends.",
                            "SOIL_CHANGE:" + nodeKey, 30, null
                        );
                        lastNotifiedSoilMap.put(nodeKey, soilMoisture);
                    }
                } else {
                    lastNotifiedSoilMap.put(nodeKey, soilMoisture);
                }
            }

            // Ultrasonic critical / recovery transitions
            boolean wasWaterCritical = Boolean.TRUE.equals(nodeWaterCriticalState.get(nodeKey));
            if (!wasWaterCritical && waterLevelCm <= ULTRASONIC_CRITICAL_CM) {
                nodeWaterCriticalState.put(nodeKey, true);
                alertService.createNotification(
                    "WATER_LEVEL_CRITICAL",
                    "Water Level Critical: " + String.format("%.1f cm", waterLevelCm),
                    String.format("Critical water level deficiency on %s (%s): %.1f cm (<= %.1f cm).",
                        nodeKey, zoneName, waterLevelCm, ULTRASONIC_CRITICAL_CM),
                    "CRITICAL", "agronomic", nodeKey, fieldId,
                    node.getMonitoringZone() != null ? node.getMonitoringZone().getId() : null,
                    zoneName, "THRESHOLD_ENGINE", "ArvaCore Autonomous AWD Engine",
                    "ZONE", "Reflood field.",
                    "WATER_CRIT:" + nodeKey, 30, null
                );
                lastNotifiedWaterMap.put(nodeKey, waterLevelCm);
            } else if (wasWaterCritical && waterLevelCm >= ULTRASONIC_RECOVERY_CM) {
                nodeWaterCriticalState.put(nodeKey, false);
                alertService.createNotification(
                    "WATER_LEVEL_RECOVERED",
                    "Water Level Recovered: " + String.format("%.1f cm", waterLevelCm),
                    String.format("Water level on %s (%s) recovered to %.1f cm (>= %.1f cm safe depth).",
                        nodeKey, zoneName, waterLevelCm, ULTRASONIC_RECOVERY_CM),
                    "SUCCESS", "agronomic", nodeKey, fieldId,
                    node.getMonitoringZone() != null ? node.getMonitoringZone().getId() : null,
                    zoneName, "THRESHOLD_ENGINE", "ArvaCore Autonomous AWD Engine",
                    "ZONE", "Field depth normal.",
                    "WATER_REC:" + nodeKey, 30, null
                );
                lastNotifiedWaterMap.put(nodeKey, waterLevelCm);
            } else {
                Double prevWater = lastNotifiedWaterMap.get(nodeKey);
                if (prevWater != null) {
                    double waterDiff = waterLevelCm - prevWater;
                    if (Math.abs(waterDiff) >= WATER_LEVEL_CHANGE_THRESHOLD_CM) {
                        String dir = waterDiff > 0 ? "rose" : "dropped";
                        String sev = waterLevelCm <= ULTRASONIC_CRITICAL_CM ? "CRITICAL" : (Math.abs(waterDiff) >= 15 ? "WARNING" : "INFO");
                        alertService.createNotification(
                            "WATER_LEVEL_CHANGE",
                            String.format("Water Level %s by %.1f cm", dir, Math.abs(waterDiff)),
                            String.format("Water level on node %s (%s) %s from %.1f cm to %.1f cm (Δ%.1f cm).",
                                nodeKey, zoneName, dir, prevWater, waterLevelCm, waterDiff),
                            sev, "agronomic", nodeKey, fieldId,
                            node.getMonitoringZone() != null ? node.getMonitoringZone().getId() : null,
                            zoneName, "THRESHOLD_ENGINE", "ArvaCore Threshold Monitor",
                            "ZONE", "Monitor water level trends.",
                            "WATER_CHANGE:" + nodeKey, 30, null
                        );
                        lastNotifiedWaterMap.put(nodeKey, waterLevelCm);
                    }
                } else {
                    lastNotifiedWaterMap.put(nodeKey, waterLevelCm);
                }
            }
        }
        // 3. Rule Evaluation:
        // Table:
        // Critical water deficiency -> CRITICAL -> START
        // Low water -> WARNING -> START if permitted
        // Sufficient water -> INFO -> STOP if active
        // Excess water -> WARNING -> STOP
        // High-water conditions override low-moisture requests.
        // Hysteresis & cooldown apply.

        double excessWaterThreshold = targetFloodDepth + 3.0; // Excess water > 8cm
        double sufficientWaterThreshold = targetFloodDepth;   // Sufficient water >= 5cm
        double lowWaterDepth = 1.0;                           // Depth < 1cm
        double criticalMoisture = minSoilMoisture - 10.0;     // Critical moisture < 20%

        String actionToTake = null;
        String reason = null;
        String severity = "INFO";

        if (waterLevelCm > excessWaterThreshold) {
            // Excess water: STOP
            actionToTake = "STOP_IRRIGATION";
            reason = String.format("Excess water detected (%.1f cm > %.1f cm)", waterLevelCm, excessWaterThreshold);
            severity = "WARNING";
        } else if (waterLevelCm >= sufficientWaterThreshold && soilMoisture >= minSoilMoisture) {
            // Sufficient water: STOP if active
            String lastCmd = lastCommandTypeMap.get(node.getNodeId());
            if ("START_IRRIGATION".equals(lastCmd)) {
                actionToTake = "STOP_IRRIGATION";
                reason = String.format("Sufficient water reached (depth: %.1f cm, moisture: %.1f%%)", waterLevelCm, soilMoisture);
                severity = "INFO";
            }
        } else if (soilMoisture < criticalMoisture || waterLevelCm <= -15.0) {
            // Critical water deficiency: START
            // High water overrides: Only start if water level is not high
            if (waterLevelCm < targetFloodDepth) {
                actionToTake = "START_IRRIGATION";
                reason = String.format("Critical water deficiency: moisture %.1f%%, water level %.1f cm", soilMoisture, waterLevelCm);
                severity = "CRITICAL";
            }
        } else if (soilMoisture < minSoilMoisture || waterLevelCm < lowWaterDepth) {
            // Low water: START if permitted
            if (waterLevelCm < targetFloodDepth) {
                actionToTake = "START_IRRIGATION";
                reason = String.format("Low water threshold reached: moisture %.1f%% (< %.1f%%), water level %.1f cm",
                        soilMoisture, minSoilMoisture, waterLevelCm);
                severity = "WARNING";
            }
        }

        if (actionToTake != null) {
            // Check cooldown & duplicate prevention
            String lastCmd = lastCommandTypeMap.get(node.getNodeId());
            LocalDateTime lastTime = lastCommandTimeMap.get(node.getNodeId());
            LocalDateTime now = LocalDateTime.now();

            if (actionToTake.equals(lastCmd)) {
                // Repeated identical action - skip duplicate dispatch
                return;
            }

            if (lastTime != null && now.isBefore(lastTime.plusMinutes(minCooldownMinutes))) {
                if (!"CRITICAL".equalsIgnoreCase(severity) && !"SAFE_STOP".equalsIgnoreCase(actionToTake)) {
                    log.info("Skipping {} command due to cooldown period ({} min)", actionToTake, minCooldownMinutes);
                    return;
                }
            }

            dispatchCommand(node, fieldId, actionToTake, reason, severity, maxDurationMinutes);
        }
    }

    private void dispatchCommand(EdgeNode node, Long fieldId, String action, String reason, String severity, int durationMinutes) {
        String correlationId = UUID.randomUUID().toString();
        log.info("Automated decision dispatching {} for node: {}, correlationId: {}, reason: {}",
                action, node.getNodeId(), correlationId, reason);

        // 1. Build Payload
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(Map.of(
                    "command", action,
                    "correlationId", correlationId,
                    "fieldId", fieldId,
                    "nodeId", node.getNodeId(),
                    "reason", reason,
                    "durationMinutes", durationMinutes,
                    "timestamp", LocalDateTime.now().toString()
            ));
        } catch (Exception e) {
            payloadJson = String.format("{\"command\":\"%s\",\"correlationId\":\"%s\",\"reason\":\"%s\"}",
                    action, correlationId, reason);
        }

        // 2. Queue Downlink for ArvaCore
        downlinkQueueService.queueDownlink(node, payloadJson, correlationId);

        // 3. Record decision
        if (decisionRepository != null) {
            try {
                IrrigationDecision decision = IrrigationDecision.builder()
                        .edgeNode(node)
                        .decisionType("START_IRRIGATION".equals(action) ? DecisionType.SOIL_MOISTURE_TRIGGER : DecisionType.WEATHER_OVERRIDE)
                        .triggerReason(TriggerReason.SOIL_MOISTURE_BELOW_MIN)
                        .requestedDurationMinutes(durationMinutes)
                        .executionStatus("QUEUED")
                        .failureReason(null)
                        .correlationId(correlationId)
                        .nodeTimestamp(LocalDateTime.now())
                        .build();
                decisionRepository.save(decision);
            } catch (Exception e) {
                log.warn("Failed to record automated irrigation decision: {}", e.getMessage());
            }
        }

        // 4. Create Notification
        if (alertService != null) {
            String title = switch (action) {
                case "START_IRRIGATION" -> "Automated Irrigation Triggered";
                case "STOP_IRRIGATION" -> "Automated Irrigation Stopped";
                case "SAFE_STOP" -> "Sensor Safety Lockout Triggered";
                default -> "Irrigation Signal: " + action;
            };

            alertService.createNotification(
                    action,
                    title,
                    reason,
                    severity,
                    "irrigation",
                    node.getNodeId(),
                    fieldId,
                    node.getMonitoringZone() != null ? node.getMonitoringZone().getId() : null,
                    node.getMonitoringZone() != null ? node.getMonitoringZone().getCode() : null,
                    "AUTOMATED_ENGINE",
                    "ArvaCore Autonomous AWD Engine",
                    "ZONE",
                    "START_IRRIGATION".equals(action) ? "Monitoring pump actuation and field water depth." : "System idle.",
                    "AUTO_DECISION:" + node.getNodeId() + ":" + action,
                    15,
                    null
            );
        }

        // 5. Update cache trackers
        lastCommandTypeMap.put(node.getNodeId(), action);
        lastCommandTimeMap.put(node.getNodeId(), LocalDateTime.now());
    }
}
