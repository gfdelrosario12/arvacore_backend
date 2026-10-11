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
                : request.getNodeId()));

        if (targetIdentifier == null || targetIdentifier.isBlank()) {
            throw new ValidationException("UNRESOLVED_NODE_IDENTITY: Node identification parameter missing");
        }

        EdgeNode node = resolveEdgeNode(targetIdentifier);

        // Validate active operational invariant
        edgeNodeRegistryService.validateNodeActiveForOperations(node.getId());

        // LoRaWAN Frame Counter Duplicate Detection
        Long fCnt = decoded.getFCnt();
        if (fCnt != null) {
            Long lastFCnt = lastSeenFrameCounters.get(node.getNodeId());
            if (lastFCnt != null && lastFCnt >= fCnt) {
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

        Long fieldId = (node.getMonitoringZone() != null && node.getMonitoringZone().getField() != null)
                ? node.getMonitoringZone().getField().getId() : null;

        try {
            systemEventPublisher.publishTelemetryReceived(node.getNodeId(), fieldId, count);
        } catch (Exception e) {
            // Asynchronous event publication error fallback
        }

        return TelemetryUplinkResponse.builder()
                .success(true)
                .nodeId(node.getNodeId())
                .message(rejected == 0
                        ? "Telemetry uplink ingested successfully"
                        : "Telemetry ingested; " + rejected + " out-of-range measurement(s) rejected")
                .duplicate(false)
                .readingsIngested(count)
                .timestamp(validatedTimestamp)
                .build();
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

