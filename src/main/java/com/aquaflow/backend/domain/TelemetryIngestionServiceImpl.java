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
        }

        Double waterDistance = null;
        if (decoded.getMeasurements() != null && decoded.getMeasurements().containsKey(SensorType.WATER_LEVEL_DISTANCE)) {
            waterDistance = decoded.getMeasurements().get(SensorType.WATER_LEVEL_DISTANCE);
        } else if (request != null && request.getWaterLevelDistance() != null) {
            waterDistance = request.getWaterLevelDistance();
        }

        Double waterLevelCm = null;
        if (request != null && request.getMeasurements() != null) {
            if (request.getMeasurements().containsKey("waterLevelCm")) {
                waterLevelCm = request.getMeasurements().get("waterLevelCm");
            } else if (request.getMeasurements().containsKey("water_level_cm")) {
                waterLevelCm = request.getMeasurements().get("water_level_cm");
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
                    soilMoisture, waterLevelCm, waterDistance, batteryLevel, batteryVoltage, rssiDbm, snrDb, 0
            );
            throw new ValidationException("UNRESOLVED_NODE_IDENTITY: Node identification parameter missing");
        }

        if (!nodePairingService.isMasterEnabled()) {
            recordStreamEvent(
                    targetIdentifier, "REJECTED", "MASTER_OFF: Master system is turned OFF. Telemetry ingestion is suspended.",
                    rawPayloadStr, seqNum, decoded.getTimestamp(), null, null, null,
                    soilMoisture, waterLevelCm, waterDistance, batteryLevel, batteryVoltage, rssiDbm, snrDb, 0
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
                    soilMoisture, waterLevelCm, waterDistance, batteryLevel, batteryVoltage, rssiDbm, snrDb, 0
            );
            throw e;
        }

        Field currentField = (node.getMonitoringZone() != null) ? node.getMonitoringZone().getField() : null;
        Long fieldId = currentField != null ? currentField.getId() : null;
        Long zoneId = (node.getMonitoringZone() != null) ? node.getMonitoringZone().getId() : null;
        String zoneCode = (node.getMonitoringZone() != null) ? node.getMonitoringZone().getName() : null;

        // LoRaWAN Frame Counter Duplicate Detection
        Long fCnt = decoded.getFCnt();
        if (fCnt != null) {
            Long lastFCnt = lastSeenFrameCounters.get(node.getNodeId());
            if (lastFCnt != null && lastFCnt >= fCnt) {
                recordStreamEvent(
                        node.getNodeId(), "PROCESSED", "Duplicate frame counter " + fCnt + " ignored",
                        rawPayloadStr, seqNum, decoded.getTimestamp(), fieldId, zoneId, zoneCode,
                        soilMoisture, waterLevelCm, waterDistance, batteryLevel, batteryVoltage, rssiDbm, snrDb, 0
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
        updateNodeHealthAndMetrics(node, decoded, validatedTimestamp);

        try {
            systemEventPublisher.publishTelemetryReceived(node.getNodeId(), fieldId, count);
        } catch (Exception e) {
            // Asynchronous event publication error fallback
        }

        String status = (rejected > 0 && count > 0) ? "PARTIALLY_PROCESSED"
                : (rejected > 0 && count == 0 ? "REJECTED" : "PROCESSED");
        String message = rejected == 0
                ? "Telemetry uplink ingested successfully"
                : "Telemetry ingested; " + rejected + " out-of-range measurement(s) rejected";

        recordStreamEvent(
                node.getNodeId(), status, message,
                rawPayloadStr, seqNum, validatedTimestamp, fieldId, zoneId, zoneCode,
                soilMoisture, waterLevelCm, waterDistance, batteryLevel, batteryVoltage, rssiDbm, snrDb, count
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
                    .statusMessage(message)
                    .rawPayload(rawPayload)
                    .build();

            telemetryStreamService.recordStreamEvent(event);
        } catch (Exception ex) {
            log.error("Failed to record telemetry stream event for node {}: {}", nodeId, ex.getMessage());
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

    private void updateNodeHealthAndMetrics(EdgeNode node, DecodedTelemetryPayload decoded, LocalDateTime timestamp) {
        NodeHealthMetrics metrics = node.getHealthMetrics();
        if (metrics == null) {
            metrics = new NodeHealthMetrics();
        }

        metrics.setLastHeartbeat(timestamp);

        if (decoded.getBatteryLevel() != null) {
            metrics.setBatteryLevel(decoded.getBatteryLevel());
        }
        if (decoded.getSolarVoltage() != null) {
            metrics.setSolarVoltage(decoded.getSolarVoltage());
        }
        if (decoded.getSignalMetadata() != null && decoded.getSignalMetadata().getRssiDbm() != null) {
            metrics.setSignalDbm(decoded.getSignalMetadata().getRssiDbm());
        }

        node.setHealthMetrics(metrics);
        if (metrics.getBatteryLevel() != null && metrics.getBatteryLevel() < 15.0) {
            node.setHealthState(HealthState.WARNING);
        } else {
            node.setHealthState(HealthState.HEALTHY);
        }

        edgeNodeRepository.save(node);
    }
}

