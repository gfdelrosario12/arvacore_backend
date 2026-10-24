package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.DecodedTelemetryPayload;
import com.aquaflow.backend.dto.request.TelemetryBatchUplinkRequest;
import com.aquaflow.backend.dto.request.TelemetryUplinkRequest;
import com.aquaflow.backend.dto.response.TelemetryBatchUplinkResponse;
import com.aquaflow.backend.dto.response.TelemetryUplinkResponse;
import com.aquaflow.backend.entity.*;
import com.aquaflow.backend.infrastructure.exception.ResourceNotFoundException;
import com.aquaflow.backend.infrastructure.exception.ValidationException;
import com.aquaflow.backend.persistence.EdgeNodeRepository;
import com.aquaflow.backend.persistence.MonitoringPointRepository;
import com.aquaflow.backend.persistence.TelemetryReadingRepository;
import com.aquaflow.backend.infrastructure.event.SystemEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Transactional
@lombok.extern.slf4j.Slf4j
public class TelemetryIngestionServiceImpl implements TelemetryIngestionService {

    private final EdgeNodeRepository edgeNodeRepository;
    private final MonitoringPointRepository monitoringPointRepository;
    private final TelemetryReadingRepository telemetryReadingRepository;
    private final EdgeNodeRegistryService edgeNodeRegistryService;
    private final PayloadDecoderRegistry payloadDecoderRegistry;
    private final SystemEventPublisher systemEventPublisher;
    private final NodePairingService nodePairingService;
    private final TelemetryStreamService telemetryStreamService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AlertService alertService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ThresholdEvaluationService thresholdEvaluationService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.aquaflow.backend.infrastructure.realtime.RealtimeSseBroadcaster sseBroadcaster;

    // Frame Counter deduplication cache per node
    private final Map<String, Long> lastSeenFrameCounters = new ConcurrentHashMap<>();

    @Override
    public TelemetryUplinkResponse ingestUplink(TelemetryUplinkRequest request) {
        return processSingleUplink(null, request);
    }

    @Override
    public TelemetryUplinkResponse ingestUplinkForNode(String nodeId, TelemetryUplinkRequest request) {
        return processSingleUplink(nodeId, request);
    }

    @Override
    public TelemetryBatchUplinkResponse ingestBatchUplink(TelemetryBatchUplinkRequest request) {
        List<TelemetryUplinkResponse> results = new ArrayList<>();
        int successful = 0;
        int failed = 0;
        int duplicates = 0;

        if (request != null && request.getUplinks() != null) {
            for (TelemetryUplinkRequest req : request.getUplinks()) {
                try {
                    TelemetryUplinkResponse res = processSingleUplink(null, req);
                    results.add(res);
                    if (res.isDuplicate()) {
                        duplicates++;
                    } else if (res.isSuccess()) {
                        successful++;
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    failed++;
                    results.add(TelemetryUplinkResponse.builder()
                            .success(false)
                            .nodeId(req.getNodeId())
                            .message("Failed to ingest: " + e.getMessage())
                            .duplicate(false)
                            .readingsIngested(0)
                            .timestamp(LocalDateTime.now())
                            .build());
                }
            }
        }

        return TelemetryBatchUplinkResponse.builder()
                .totalProcessed(results.size())
                .successful(successful)
                .failed(failed)
                .duplicates(duplicates)
                .results(results)
                .build();
    }

    private TelemetryUplinkResponse processSingleUplink(String explicitNodeId, TelemetryUplinkRequest request) {
        DecodedTelemetryPayload decoded = payloadDecoderRegistry.decode(request);

        String targetIdentifier = explicitNodeId != null ? explicitNodeId
                : (decoded.getNodeId() != null ? decoded.getNodeId()
                : (decoded.getDevEui() != null ? decoded.getDevEui()
                : (request != null ? request.getNodeId() : null)));

        String rawPayloadStr = null;
        try {
            rawPayloadStr = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            rawPayloadStr = String.valueOf(request);
        }

        Double soilMoisture = null;
        if (decoded.getMeasurements() != null && decoded.getMeasurements().containsKey(SensorType.SOIL_MOISTURE)) {
            soilMoisture = decoded.getMeasurements().get(SensorType.SOIL_MOISTURE);
        } else if (request != null && request.getSoilMoisturePercent() != null) {
            soilMoisture = request.getSoilMoisturePercent();
        } else if (request != null && request.getSoilMoisture() != null) {
            soilMoisture = request.getSoilMoisture();
        }

        Double waterDistance = null;
        if (decoded.getMeasurements() != null && decoded.getMeasurements().containsKey(SensorType.WATER_LEVEL_DISTANCE)) {
            waterDistance = decoded.getMeasurements().get(SensorType.WATER_LEVEL_DISTANCE);
        } else if (request != null && request.getWaterLevelDistance() != null) {
            waterDistance = request.getWaterLevelDistance();
        }

        String soilHealth = extractSoilSensorHealth(decoded, request, soilMoisture);
        String ultrasonicHealth = extractUltrasonicSensorHealth(decoded, request, waterDistance);
        String telemetryStatus = resolveTelemetryStatus(decoded, request, soilHealth, ultrasonicHealth);

        Double waterLevelCm = null;
        if (request != null && request.getMeasurements() != null) {
            if (request.getMeasurements().containsKey("waterLevelCm")) {
                waterLevelCm = request.getMeasurements().get("waterLevelCm");
            } else if (request.getMeasurements().containsKey("water_level_cm")) {
                waterLevelCm = request.getMeasurements().get("water_level_cm");
            }
        }
        if (waterLevelCm == null && request != null && request.getWaterLevel() != null) {
            waterLevelCm = request.getWaterLevel();
        }
        if (waterDistance != null) {
            if (waterDistance <= 0.0 || (ultrasonicHealth != null && !"OK".equalsIgnoreCase(ultrasonicHealth) && !"UNKNOWN".equalsIgnoreCase(ultrasonicHealth))) {
                waterLevelCm = 0.0;
            } else if (waterLevelCm == null) {
                double depth = 101.6 - waterDistance;
                waterLevelCm = Math.max(0.0, Math.min(91.44, depth));
            }
        }

        Double batteryLevel = decoded.getBatteryLevel() != null ? decoded.getBatteryLevel() : (request != null ? request.getBatteryLevel() : null);
        Double batteryVoltage = null;
        if (decoded.getMeasurements() != null && decoded.getMeasurements().containsKey(SensorType.BATTERY_VOLTAGE)) {
            batteryVoltage = decoded.getMeasurements().get(SensorType.BATTERY_VOLTAGE);
        } else if (request != null && request.getBatteryVoltage() != null) {
            batteryVoltage = request.getBatteryVoltage();
        }

        Integer rssiDbm = decoded.getSignalMetadata() != null ? decoded.getSignalMetadata().getRssiDbm() : (request != null ? request.getRssiDbm() : null);
        Double snrDb = decoded.getSignalMetadata() != null ? decoded.getSignalMetadata().getSnrDb() : (request != null ? request.getSnrDb() : null);
        Long seqNum = (request != null && request.getSeq() != null) ? request.getSeq() : decoded.getFCnt();

        if (targetIdentifier == null || targetIdentifier.isBlank()) {
            recordStreamEvent(
                    "UNKNOWN", "REJECTED", "UNRESOLVED_NODE_IDENTITY: Node identification parameter missing",
                    rawPayloadStr, seqNum, decoded.getTimestamp(), null, null, null,
                    soilMoisture, waterLevelCm, waterDistance, batteryLevel, batteryVoltage, rssiDbm, snrDb,
                    soilHealth, ultrasonicHealth, telemetryStatus, 0
            );
            throw new ValidationException("UNRESOLVED_NODE_IDENTITY: Node identification parameter missing");
        }

        // Fixed provisioned nodes bypass the master-off gate; they transmit autonomously.
        boolean isFixedNode = targetIdentifier != null && (
                targetIdentifier.equals("ARVANODE-001") || targetIdentifier.equals("ARVANODE-002") ||
                targetIdentifier.equals("ARVANODE-003") || targetIdentifier.equals("ARVANODE-004") ||
                targetIdentifier.equals("ARVANODE-005") || targetIdentifier.equals("ARVANODE-006")
        );
        if (!isFixedNode && !nodePairingService.isMasterEnabled()) {
            recordStreamEvent(
                    targetIdentifier, "REJECTED", "MASTER_OFF: Master system is turned OFF. Telemetry ingestion is suspended.",
                    rawPayloadStr, seqNum, decoded.getTimestamp(), null, null, null,
                    soilMoisture, waterLevelCm, waterDistance, batteryLevel, batteryVoltage, rssiDbm, snrDb,
                    soilHealth, ultrasonicHealth, telemetryStatus, 0
            );
            throw new ValidationException("MASTER_OFF: Master system is turned OFF. Telemetry ingestion is suspended.");
        }

        EdgeNode node;
        try {
            node = resolveEdgeNode(targetIdentifier);
            edgeNodeRegistryService.validateNodeActiveForOperations(node.getId());
        } catch (Exception e) {
            recordStreamEvent(
                    targetIdentifier, "REJECTED", e.getMessage(),
                    rawPayloadStr, seqNum, decoded.getTimestamp(), null, null, null,
                    soilMoisture, waterLevelCm, waterDistance, batteryLevel, batteryVoltage, rssiDbm, snrDb,
                    soilHealth, ultrasonicHealth, telemetryStatus, 0
            );
            throw e;
        }

        // Enforce fixed zone assignment: ARVANODE-001 -> Zone 1, ARVANODE-002 -> Zone 2
        validateFixedNodeZoneBinding(node, targetIdentifier, rawPayloadStr, seqNum, decoded,
                soilMoisture, waterLevelCm, waterDistance, batteryLevel, batteryVoltage, rssiDbm, snrDb,
                soilHealth, ultrasonicHealth, telemetryStatus);

        Field currentField = (node.getMonitoringZone() != null) ? node.getMonitoringZone().getField() : null;
        Long fieldId = currentField != null ? currentField.getId() : null;
        Long zoneId = (node.getMonitoringZone() != null) ? node.getMonitoringZone().getId() : null;
        String zoneCode = (node.getMonitoringZone() != null)
                ? (node.getMonitoringZone().getCode() != null && !node.getMonitoringZone().getCode().isBlank()
                    ? node.getMonitoringZone().getCode()
                    : node.getMonitoringZone().getName())
                : null;

        // LoRaWAN Frame Counter Duplicate Detection
        Long fCnt = decoded.getFCnt();
        if (fCnt != null) {
            Long lastFCnt = lastSeenFrameCounters.get(node.getNodeId());
            if (lastFCnt != null && lastFCnt >= fCnt) {
                recordStreamEvent(
                        node.getNodeId(), "PROCESSED", "Duplicate frame counter " + fCnt + " ignored",
                        rawPayloadStr, seqNum, decoded.getTimestamp(), fieldId, zoneId, zoneCode,
                        soilMoisture, waterLevelCm, waterDistance, batteryLevel, batteryVoltage, rssiDbm, snrDb,
                        soilHealth, ultrasonicHealth, telemetryStatus, 0
                );
                return TelemetryUplinkResponse.builder()
                        .success(true)
                        .nodeId(node.getNodeId())
                        .message("Duplicate frame counter " + fCnt + " ignored")
                        .duplicate(true)
                        .readingsIngested(0)
                        .timestamp(LocalDateTime.now())
                        .build();
            }
            lastSeenFrameCounters.put(node.getNodeId(), fCnt);
        }

        // Validate timestamp drift
        LocalDateTime validatedTimestamp = validateTimestamp(decoded.getTimestamp());

        // Persist Telemetry Readings
        List<MonitoringPoint> points = monitoringPointRepository.findByEdgeNodeId(node.getId());
        MonitoringPoint primaryPoint = points.isEmpty() ? null : points.get(0);

        int count = 0;
        int rejected = 0;
        if (decoded.getMeasurements() != null) {
            for (Map.Entry<SensorType, Double> entry : decoded.getMeasurements().entrySet()) {
                SensorType type = entry.getKey();
                Double val = entry.getValue();

                if (!TelemetryValidator.isPlausible(type, val)) {
                    rejected++;
                    log.warn("Rejected out-of-range {} reading from node {}", type, node.getNodeId());
                    continue;
                }

                TelemetryReading reading = TelemetryReading.builder()
                        .edgeNode(node)
                        .field(currentField)
                        .monitoringPoint(primaryPoint)
                        .sensorType(type)
                        .valueNum(val)
                        .unit(deriveUnit(type))
                        .timestamp(validatedTimestamp)
                        .build();

                telemetryReadingRepository.save(reading);
                count++;
            }
        }

        // Update Node Health & Last-Seen Metrics
        updateNodeHealthAndMetrics(node, decoded, validatedTimestamp, soilHealth, ultrasonicHealth, telemetryStatus);

        // Evaluate automated irrigation thresholds on valid telemetry
        if (thresholdEvaluationService != null) {
            try {
                thresholdEvaluationService.evaluateTelemetryThresholds(
                        node, soilMoisture, waterLevelCm, soilHealth, ultrasonicHealth, telemetryStatus);
            } catch (Exception e) {
                // Safeguard against threshold evaluation failures interrupting ingestion
            }
        }

        try {
            systemEventPublisher.publishTelemetryReceived(node.getNodeId(), fieldId, count);
        } catch (Exception e) {
            // Asynchronous event publication error fallback
        }

        // Broadcast real-time SSE telemetry update to connected clients immediately
        if (sseBroadcaster != null) {
            try {
                com.aquaflow.backend.dto.response.NodeLatestTelemetryResponse snapshot =
                        com.aquaflow.backend.dto.response.NodeLatestTelemetryResponse.builder()
                                .edgeNodeId(node.getId())
                                .nodeId(node.getNodeId())
                                .monitoringZoneId(zoneId)
                                .monitoringZoneCode(zoneCode)
                                .monitoringZoneName(node.getMonitoringZone() != null ? node.getMonitoringZone().getName() : null)
                                .soilMoisturePercent(soilMoisture)
                                .waterLevelDistanceCm(waterDistance)
                                .waterLevelCm(waterLevelCm)
                                .batteryVoltage(batteryVoltage)
                                .batteryPercent(batteryLevel != null ? (int) Math.round(batteryLevel) : null)
                                .soilSensorHealth(soilHealth)
                                .ultrasonicSensorHealth(ultrasonicHealth)
                                .telemetryStatus(telemetryStatus)
                                .lastTelemetryAt(validatedTimestamp)
                                .lastReceivedAt(LocalDateTime.now())
                                .isOnline(true)
                                .hasTelemetry(count > 0)
                                .build();
                sseBroadcaster.broadcastTelemetryUpdated(snapshot);
            } catch (Exception e) {
                log.warn("Failed to broadcast SSE telemetry for node {}: {}", node.getNodeId(), e.getMessage());
            }
        }

        String status = (rejected > 0 && count > 0) ? "PARTIALLY_PROCESSED"
                : (rejected > 0 && count == 0 ? "REJECTED" : "PROCESSED");
        String message = rejected == 0
                ? ("OK".equalsIgnoreCase(telemetryStatus) || telemetryStatus == null
                    ? "Telemetry uplink ingested successfully"
                    : "Telemetry ingested with sensor status: " + telemetryStatus
                      + " (soil=" + soilHealth + ", ultrasonic=" + ultrasonicHealth + ")")
                : "Telemetry ingested; " + rejected + " out-of-range measurement(s) rejected";

        recordStreamEvent(
                node.getNodeId(), status, message,
                rawPayloadStr, seqNum, validatedTimestamp, fieldId, zoneId, zoneCode,
                soilMoisture, waterLevelCm, waterDistance, batteryLevel, batteryVoltage, rssiDbm, snrDb,
                soilHealth, ultrasonicHealth, telemetryStatus, count
        );

        return TelemetryUplinkResponse.builder()
                .success(true)
                .nodeId(node.getNodeId())
                .message(message)
                .duplicate(false)
                .readingsIngested(count)
                .timestamp(validatedTimestamp)
                .build();
    }

    private void recordStreamEvent(
            String nodeId,
            String status,
            String message,
            String rawPayload,
            Long sequenceNumber,
            LocalDateTime deviceTimestamp,
            Long fieldId,
            Long zoneId,
            String zoneCode,
            Double soilMoisture,
            Double waterLevelCm,
            Double waterDistance,
            Double batteryLevel,
            Double batteryVoltage,
            Integer rssiDbm,
            Double snrDb,
            String soilSensorHealth,
            String ultrasonicSensorHealth,
            String telemetryStatus,
            int readingsCount
    ) {
        try {
            TelemetryStreamEvent event = TelemetryStreamEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .nodeId(nodeId)
                    .eventType("TELEMETRY_UPLINK")
                    .status(status)
                    .receivedAt(LocalDateTime.now())
                    .deviceTimestamp(deviceTimestamp)
                    .sequenceNumber(sequenceNumber)
                    .fieldId(fieldId)
                    .zoneId(zoneId)
                    .zoneCode(zoneCode)
                    .soilMoisturePercent(soilMoisture)
                    .waterLevelCm(waterLevelCm)
                    .waterLevelDistanceCm(waterDistance)
                    .batteryLevel(batteryLevel)
                    .batteryVoltage(batteryVoltage)
                    .rssiDbm(rssiDbm)
                    .snrDb(snrDb)
                    .readingsCount(readingsCount)
                    .soilSensorHealth(soilSensorHealth)
                    .ultrasonicSensorHealth(ultrasonicSensorHealth)
                    .telemetryStatus(telemetryStatus)
                    .statusMessage(message)
                    .rawPayload(rawPayload)
                    .build();

            telemetryStreamService.recordStreamEvent(event);
        } catch (Exception ex) {
            log.error("Failed to record telemetry stream event for node {}: {}", nodeId, ex.getMessage());
        }
    }

    private String extractSoilSensorHealth(DecodedTelemetryPayload decoded, TelemetryUplinkRequest request, Double soilMoisture) {
        Map<String, String> healthMap = (decoded != null && decoded.getSensorHealth() != null && !decoded.getSensorHealth().isEmpty())
                ? decoded.getSensorHealth()
                : (request != null ? request.getSensorHealth() : null);
        if (healthMap != null) {
            for (Map.Entry<String, String> entry : healthMap.entrySet()) {
                String k = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
                if (k.contains("soil") && entry.getValue() != null && !entry.getValue().isBlank()) {
                    return entry.getValue().trim().toUpperCase(Locale.ROOT);
                }
            }
        }
        if (soilMoisture != null && soilMoisture > 0.0) {
            return "OK";
        }
        return soilMoisture != null ? "OK" : null;
    }

    private String extractUltrasonicSensorHealth(DecodedTelemetryPayload decoded, TelemetryUplinkRequest request, Double waterDistance) {
        Map<String, String> healthMap = (decoded != null && decoded.getSensorHealth() != null && !decoded.getSensorHealth().isEmpty())
                ? decoded.getSensorHealth()
                : (request != null ? request.getSensorHealth() : null);
        if (healthMap != null) {
            for (Map.Entry<String, String> entry : healthMap.entrySet()) {
                String k = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
                if ((k.contains("ultrasonic") || k.contains("water") || k.contains("distance"))
                        && entry.getValue() != null && !entry.getValue().isBlank()) {
                    return entry.getValue().trim().toUpperCase(Locale.ROOT);
                }
            }
        }
        if (waterDistance != null) {
            if (waterDistance <= 0.0) {
                return "ERROR";
            }
            return "OK";
        }
        return null;
    }

    private String resolveTelemetryStatus(
            DecodedTelemetryPayload decoded,
            TelemetryUplinkRequest request,
            String soilHealth,
            String ultrasonicHealth
    ) {
        String explicit = (decoded != null && decoded.getTelemetryStatus() != null && !decoded.getTelemetryStatus().isBlank())
                ? decoded.getTelemetryStatus().trim().toUpperCase(Locale.ROOT)
                : (request != null && request.getTelemetryStatus() != null && !request.getTelemetryStatus().isBlank()
                    ? request.getTelemetryStatus().trim().toUpperCase(Locale.ROOT)
                    : null);
        if (explicit != null) {
            return explicit;
        }
        boolean soilFailed = soilHealth != null && !"OK".equalsIgnoreCase(soilHealth) && !"UNKNOWN".equalsIgnoreCase(soilHealth);
        boolean ultraFailed = ultrasonicHealth != null && !"OK".equalsIgnoreCase(ultrasonicHealth) && !"UNKNOWN".equalsIgnoreCase(ultrasonicHealth);
        if (soilFailed && ultraFailed) {
            return "SENSOR_FAILURE";
        }
        if (soilFailed || ultraFailed) {
            return "PARTIAL_FAILURE";
        }
        if ("OK".equalsIgnoreCase(soilHealth) || "OK".equalsIgnoreCase(ultrasonicHealth)) {
            return "OK";
        }
        return null;
    }

    /**
     * Fixed node → zone binding table.
     * ARVANODE-001 must be assigned to monitoring_zone_id=1 (Zone 1 / Q1).
     * ARVANODE-002 must be assigned to monitoring_zone_id=2 (Zone 2 / Q2).
     * Rejects telemetry if the zone assignment is missing or incorrect.
     */
    private static final java.util.Map<String, Long> FIXED_NODE_ZONE = java.util.Map.of(
            "ARVANODE-001", 1L,
            "ARVANODE-002", 2L,
            "ARVANODE-003", 3L,
            "ARVANODE-004", 4L,
            "ARVANODE-005", 5L,
            "ARVANODE-006", 6L
    );

    private void validateFixedNodeZoneBinding(
            EdgeNode node, String targetIdentifier, String rawPayloadStr, Long seqNum,
            DecodedTelemetryPayload decoded,
            Double soilMoisture, Double waterLevelCm, Double waterDistance,
            Double batteryLevel, Double batteryVoltage, Integer rssiDbm, Double snrDb,
            String soilHealth, String ultrasonicHealth, String telemetryStatus
    ) {
        Long expectedZoneId = FIXED_NODE_ZONE.get(node.getNodeId());
        if (expectedZoneId == null) {
            return; // Not a fixed node — no constraint
        }
        Long actualZoneId = node.getMonitoringZone() != null ? node.getMonitoringZone().getId() : null;
        if (!expectedZoneId.equals(actualZoneId)) {
            String msg = String.format(
                    "FIXED_ZONE_MISMATCH: %s must be assigned to zone %d but is currently assigned to zone %s. " +
                    "Run migration V21 to restore the permanent assignment.",
                    node.getNodeId(), expectedZoneId, actualZoneId
            );
            log.error(msg);
            recordStreamEvent(
                    targetIdentifier, "REJECTED", msg,
                    rawPayloadStr, seqNum, decoded.getTimestamp(), null, null, null,
                    soilMoisture, waterLevelCm, waterDistance, batteryLevel, batteryVoltage, rssiDbm, snrDb,
                    soilHealth, ultrasonicHealth, telemetryStatus, 0
            );
            throw new ValidationException(msg);
        }
    }

    private EdgeNode resolveEdgeNode(String identifier) {
        return edgeNodeRepository.findByNodeId(identifier)
                .or(() -> edgeNodeRepository.findByIdentityMacAddress(identifier))
                .or(() -> edgeNodeRepository.findByIdentitySerialNumber(identifier))
                .orElseThrow(() -> new ResourceNotFoundException("Edge node not found for identifier: " + identifier));
    }

    private LocalDateTime validateTimestamp(LocalDateTime ts) {
        if (ts == null) {
            return LocalDateTime.now();
        }
        LocalDateTime now = LocalDateTime.now();
        if (ts.isBefore(now.minusHours(24)) || ts.isAfter(now.plusMinutes(5))) {
            return now;
        }
        return ts;
    }

    private String deriveUnit(SensorType type) {
        if (type == SensorType.SOIL_MOISTURE || type == SensorType.HUMIDITY) {
            return "PERCENT";
        } else if (type == SensorType.TEMPERATURE) {
            return "CELSIUS";
        } else if (type == SensorType.WATER_FLOW) {
            return "LITERS_PER_MINUTE";
        } else if (type == SensorType.WATER_LEVEL_DISTANCE) {
            return "CENTIMETERS";
        }
        return "UNIT";
    }

    private void updateNodeHealthAndMetrics(
            EdgeNode node,
            DecodedTelemetryPayload decoded,
            LocalDateTime timestamp,
            String soilHealth,
            String ultrasonicHealth,
            String telemetryStatus
    ) {
        NodeHealthMetrics metrics = node.getHealthMetrics();
        if (metrics == null) {
            metrics = new NodeHealthMetrics();
        }

        String previousSoilHealth = metrics.getSoilSensorHealth();
        String previousUltrasonicHealth = metrics.getUltrasonicSensorHealth();
        HealthState previousHealthState = node.getHealthState();

        metrics.setLastHeartbeat(timestamp);
        metrics.setLastTelemetryAt(timestamp);
        metrics.setIsTelemetryStale(false);

        if (decoded.getBatteryLevel() != null) {
            metrics.setBatteryLevel(decoded.getBatteryLevel());
        }
        if (decoded.getSolarVoltage() != null) {
            metrics.setSolarVoltage(decoded.getSolarVoltage());
        }
        if (decoded.getSignalMetadata() != null && decoded.getSignalMetadata().getRssiDbm() != null) {
            metrics.setSignalDbm(decoded.getSignalMetadata().getRssiDbm());
        }
        if (soilHealth != null) {
            metrics.setSoilSensorHealth(soilHealth);
        }
        if (ultrasonicHealth != null) {
            metrics.setUltrasonicSensorHealth(ultrasonicHealth);
        }
        if (telemetryStatus != null) {
            metrics.setTelemetryStatus(telemetryStatus);
        }

        node.setHealthMetrics(metrics);

        String effectiveSoil = metrics.getSoilSensorHealth();
        String effectiveUltra = metrics.getUltrasonicSensorHealth();
        boolean soilFailed = effectiveSoil != null && !"OK".equalsIgnoreCase(effectiveSoil) && !"UNKNOWN".equalsIgnoreCase(effectiveSoil);
        boolean ultraFailed = effectiveUltra != null && !"OK".equalsIgnoreCase(effectiveUltra) && !"UNKNOWN".equalsIgnoreCase(effectiveUltra);

        if (soilFailed && ultraFailed) {
            node.setHealthState(HealthState.CRITICAL);
        } else if (soilFailed || ultraFailed) {
            node.setHealthState(HealthState.WARNING);
        } else if (metrics.getBatteryLevel() != null && metrics.getBatteryLevel() < 15.0) {
            node.setHealthState(HealthState.WARNING);
        } else {
            node.setHealthState(HealthState.HEALTHY);
        }

        edgeNodeRepository.save(node);

        emitSensorTransitionNotifications(
                node,
                previousSoilHealth,
                effectiveSoil,
                previousUltrasonicHealth,
                effectiveUltra,
                previousHealthState,
                node.getHealthState()
        );
    }

    private void emitSensorTransitionNotifications(
            EdgeNode node,
            String prevSoil,
            String currSoil,
            String prevUltra,
            String currUltra,
            HealthState prevHealth,
            HealthState currHealth
    ) {
        if (alertService == null || node == null) return;
        try {
            Long zoneId = node.getMonitoringZone() != null ? node.getMonitoringZone().getId() : null;
            String zoneCode = node.getMonitoringZone() != null
                    ? (node.getMonitoringZone().getCode() != null && !node.getMonitoringZone().getCode().isBlank()
                        ? node.getMonitoringZone().getCode()
                        : "Q" + node.getMonitoringZone().getId())
                    : null;
            Long fieldId = (node.getMonitoringZone() != null && node.getMonitoringZone().getField() != null)
                    ? node.getMonitoringZone().getField().getId() : null;

            boolean wasOffline = prevHealth == HealthState.OFFLINE;
            if (wasOffline && currHealth != HealthState.OFFLINE) {
                alertService.createNotification(
                        "NODE_ONLINE",
                        "Node Back Online: " + node.getNodeId(),
                        String.format("Node %s resumed telemetry transmission.", node.getNodeId()),
                        "INFO",
                        "connectivity",
                        node.getNodeId(),
                        fieldId,
                        zoneId,
                        zoneCode,
                        node.getNodeId(),
                        "Node " + node.getNodeId(),
                        zoneCode != null ? zoneCode : node.getNodeId(),
                        "Verify incoming sensor readings in the ESP32 Telemetry Stream.",
                        "NODE_ONLINE:" + node.getNodeId(),
                        60,
                        null
                );
            }

            boolean prevSoilFailed = isSensorFailed(prevSoil);
            boolean currSoilFailed = isSensorFailed(currSoil);
            if (!prevSoilFailed && currSoilFailed) {
                alertService.createNotification(
                        "SOIL_SENSOR_FAILURE",
                        "Soil Moisture Sensor Fault: " + node.getNodeId(),
                        String.format("Soil moisture sensor on node %s reported %s. Transmitting fallback 0.00%% while keeping telemetry active.",
                                node.getNodeId(), currSoil),
                        "WARNING",
                        "hardware",
                        node.getNodeId(),
                        fieldId,
                        zoneId,
                        zoneCode,
                        node.getNodeId(),
                        "Node " + node.getNodeId(),
                        zoneCode != null ? zoneCode : node.getNodeId(),
                        "Inspect soil moisture probe wiring and ADC connection on " + node.getNodeId() + ".",
                        "SENSOR_FAULT:" + node.getNodeId() + ":SOIL:" + currSoil,
                        120,
                        null
                );
            } else if (prevSoilFailed && "OK".equalsIgnoreCase(currSoil)) {
                alertService.createNotification(
                        "SOIL_SENSOR_RECOVERED",
                        "Soil Moisture Sensor Recovered: " + node.getNodeId(),
                        String.format("Soil moisture sensor on node %s recovered from %s to OK.", node.getNodeId(), prevSoil),
                        "INFO",
                        "hardware",
                        node.getNodeId(),
                        fieldId,
                        zoneId,
                        zoneCode,
                        node.getNodeId(),
                        "Node " + node.getNodeId(),
                        zoneCode != null ? zoneCode : node.getNodeId(),
                        "Sensor readings are now included in zone averages and irrigation evaluations.",
                        "SENSOR_RECOVERY:" + node.getNodeId() + ":SOIL",
                        30,
                        null
                );
            }

            boolean prevUltraFailed = isSensorFailed(prevUltra);
            boolean currUltraFailed = isSensorFailed(currUltra);
            if (!prevUltraFailed && currUltraFailed) {
                alertService.createNotification(
                        "ULTRASONIC_SENSOR_FAILURE",
                        "Ultrasonic Water Sensor Fault: " + node.getNodeId(),
                        String.format("Ultrasonic water-level sensor on node %s reported %s. Transmitting fallback 0.00 cm while keeping telemetry active.",
                                node.getNodeId(), currUltra),
                        currSoilFailed ? "CRITICAL" : "WARNING",
                        "hardware",
                        node.getNodeId(),
                        fieldId,
                        zoneId,
                        zoneCode,
                        node.getNodeId(),
                        "Node " + node.getNodeId(),
                        zoneCode != null ? zoneCode : node.getNodeId(),
                        "Inspect ultrasonic TRIG/ECHO pins and power supply on " + node.getNodeId() + ".",
                        "SENSOR_FAULT:" + node.getNodeId() + ":ULTRA:" + currUltra,
                        120,
                        null
                );
            } else if (prevUltraFailed && "OK".equalsIgnoreCase(currUltra)) {
                alertService.createNotification(
                        "ULTRASONIC_SENSOR_RECOVERED",
                        "Ultrasonic Water Sensor Recovered: " + node.getNodeId(),
                        String.format("Ultrasonic water-level sensor on node %s recovered from %s to OK.", node.getNodeId(), prevUltra),
                        "INFO",
                        "hardware",
                        node.getNodeId(),
                        fieldId,
                        zoneId,
                        zoneCode,
                        node.getNodeId(),
                        "Node " + node.getNodeId(),
                        zoneCode != null ? zoneCode : node.getNodeId(),
                        "Water level measurements are now active for zone " + (zoneCode != null ? zoneCode : node.getNodeId()) + ".",
                        "SENSOR_RECOVERY:" + node.getNodeId() + ":ULTRA",
                        30,
                        null
                );
            }
        } catch (Exception e) {
            log.warn("Failed to emit sensor transition notification for node {}: {}", node.getNodeId(), e.getMessage());
        }
    }

    private boolean isSensorFailed(String status) {
        return status != null && !status.isBlank()
                && !"OK".equalsIgnoreCase(status)
                && !"UNKNOWN".equalsIgnoreCase(status);
    }

    @Override
    public void clearNodeCache(String nodeId) {
        if (nodeId != null) {
            lastSeenFrameCounters.remove(nodeId);
            log.debug("Cleared telemetry frame counter cache for node: {}", nodeId);
        }
    }
}

