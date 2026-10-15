package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.request.*;
import com.aquaflow.backend.dto.response.*;
import com.aquaflow.backend.entity.*;
import com.aquaflow.backend.infrastructure.exception.ResourceNotFoundException;
import com.aquaflow.backend.infrastructure.exception.ValidationException;
import com.aquaflow.backend.infrastructure.util.DtoMapper;
import com.aquaflow.backend.persistence.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class EdgeNodeRegistryServiceImpl implements EdgeNodeRegistryService {

    private final EdgeNodeRepository edgeNodeRepository;
    private final MonitoringZoneRepository monitoringZoneRepository;
    private final MonitoringPointRepository monitoringPointRepository;
    private final TelemetryReadingRepository telemetryReadingRepository;
    private final AutoIrrigationConfigRepository autoIrrigationConfigRepository;
    private final IrrigationAuditLogRepository auditLogRepository;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public EdgeNodeResponse registerNode(RegisterNodeRequest request) {
        if (edgeNodeRepository.findByNodeId(request.getNodeId()).isPresent()) {
            throw new ValidationException("Edge node with ID " + request.getNodeId() + " already exists");
        }

        MonitoringZone zone = null;
        if (request.getMonitoringZoneId() != null) {
            zone = monitoringZoneRepository.findById(request.getMonitoringZoneId()).orElse(null);
            if (zone == null) {
                log.warn("Monitoring zone with id {} not found; registering node unassigned", request.getMonitoringZoneId());
            }
        }

        CommunicationIdentity identity = CommunicationIdentity.builder()
                .identityType(request.getIdentityType())
                .identityValue(request.getIdentityValue())
                .macAddress(request.getMacAddress())
                .serialNumber(request.getSerialNumber())
                .build();

        NodeHealthMetrics metrics = NodeHealthMetrics.builder()
                .batteryLevel(100.0)
                .solarVoltage(5.0)
                .signalDbm(-60)
                .lastHeartbeat(LocalDateTime.now())
                .build();

        EdgeNode node = EdgeNode.builder()
                .nodeId(request.getNodeId())
                .identity(identity)
                .lifecycleState(NodeLifecycleState.PROVISIONED)
                .healthState(HealthState.HEALTHY)
                .healthMetrics(metrics)
                .monitoringZone(zone)
                .hardwareModel(request.getHardwareModel())
                .firmwareVersion(request.getFirmwareVersion())
                .build();

        EdgeNode savedNode = edgeNodeRepository.save(node);
        return DtoMapper.toEdgeNodeResponse(savedNode);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EdgeNodeResponse> getNodes(NodeLifecycleState lifecycleState, HealthState healthState, Long zoneId) {
        List<EdgeNode> nodes = edgeNodeRepository.findAll();

        return nodes.stream()
                .filter(n -> lifecycleState == null || n.getLifecycleState() == lifecycleState)
                .filter(n -> healthState == null || n.getHealthState() == healthState)
                .filter(n -> zoneId == null || (n.getMonitoringZone() != null && Objects.equals(n.getMonitoringZone().getId(), zoneId)))
                .map(DtoMapper::toEdgeNodeResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EdgeNodeResponse> getNodesPaginated(NodeLifecycleState lifecycleState, HealthState healthState, Long zoneId, Pageable pageable) {
        List<EdgeNodeResponse> allFiltered = getNodes(lifecycleState, healthState, zoneId);
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allFiltered.size());
        if (start > allFiltered.size()) {
            return new PageImpl<>(Collections.emptyList(), pageable, allFiltered.size());
        }
        return new PageImpl<>(allFiltered.subList(start, end), pageable, allFiltered.size());
    }

    @Override
    @Transactional(readOnly = true)
    public EdgeNodeResponse getNodeById(Long id) {
        EdgeNode node = findNodeOrThrow(id);
        return DtoMapper.toEdgeNodeResponse(node);
    }

    @Override
    public EdgeNodeResponse updateNode(Long id, RegisterNodeRequest request) {
        EdgeNode node = findNodeOrThrow(id);

        if (request.getMonitoringZoneId() != null) {
            MonitoringZone zone = monitoringZoneRepository.findById(request.getMonitoringZoneId()).orElse(null);
            if (zone != null) {
                node.setMonitoringZone(zone);
            } else {
                log.warn("Monitoring zone with id {} not found during update; skipping zone assignment", request.getMonitoringZoneId());
            }
        }

        if (request.getHardwareModel() != null) {
            node.setHardwareModel(request.getHardwareModel());
        }
        if (request.getFirmwareVersion() != null) {
            node.setFirmwareVersion(request.getFirmwareVersion());
        }

        if (node.getIdentity() != null) {
            if (request.getIdentityType() != null) node.getIdentity().setIdentityType(request.getIdentityType());
            if (request.getIdentityValue() != null) node.getIdentity().setIdentityValue(request.getIdentityValue());
            if (request.getMacAddress() != null) node.getIdentity().setMacAddress(request.getMacAddress());
            if (request.getSerialNumber() != null) node.getIdentity().setSerialNumber(request.getSerialNumber());
        }

        EdgeNode updated = edgeNodeRepository.save(node);
        return DtoMapper.toEdgeNodeResponse(updated);
    }

    @Override
    public EdgeNodeResponse assignNodeToField(Long id, AssignNodeFieldRequest request) {
        EdgeNode node = findNodeOrThrow(id);

        if (request.getMonitoringZoneId() != null) {
            MonitoringZone zone = monitoringZoneRepository.findById(request.getMonitoringZoneId())
                    .orElseThrow(() -> new ResourceNotFoundException("Monitoring zone not found: " + request.getMonitoringZoneId()));

            if (request.getFieldId() != null && zone.getField() != null) {
                if (!request.getFieldId().equals(zone.getField().getId())) {
                    throw new ValidationException(String.format("Monitoring zone %d belongs to field %d, not requested field %d",
                            zone.getId(), zone.getField().getId(), request.getFieldId()));
                }
            }

            Long previousZoneId = node.getMonitoringZone() != null ? node.getMonitoringZone().getId() : null;
            node.setMonitoringZone(zone);
            EdgeNode saved = edgeNodeRepository.save(node);

            // Audit log
            IrrigationAuditLog auditLog = IrrigationAuditLog.builder()
                    .eventType("NODE_FIELD_ASSIGNMENT_CHANGED")
                    .actor("ARVALINK_ADMIN")
                    .entityType("EdgeNode")
                    .entityId(String.valueOf(id))
                    .payloadJson(String.format("{\"nodeId\":\"%s\",\"previousZoneId\":%s,\"newZoneId\":%d,\"fieldId\":%d}",
                            saved.getNodeId(), previousZoneId != null ? previousZoneId : "null", zone.getId(),
                            zone.getField() != null ? zone.getField().getId() : null))
                    .build();
            auditLogRepository.save(auditLog);

            log.info("Node {} successfully assigned to field {} and zone {}",
                    saved.getNodeId(), zone.getField() != null ? zone.getField().getName() : "N/A", zone.getName());
            return DtoMapper.toEdgeNodeResponse(saved);
        }

        return DtoMapper.toEdgeNodeResponse(node);
    }

    @Override
    public CommissionNodeResponse commissionNode(Long id, CommissionNodeRequest request) {
        EdgeNode node = findNodeOrThrow(id);

        if (node.getLifecycleState() == NodeLifecycleState.DECOMMISSIONED || node.getLifecycleState() == NodeLifecycleState.REPLACED) {
            throw new ValidationException("Cannot commission node in state: " + node.getLifecycleState());
        }

        if (request != null && request.getMonitoringZoneId() != null) {
            MonitoringZone zone = monitoringZoneRepository.findById(request.getMonitoringZoneId()).orElse(null);
            if (zone != null) {
                node.setMonitoringZone(zone);
            }
        }

        node.setLifecycleState(NodeLifecycleState.COMMISSIONED);
        EdgeNode saved = edgeNodeRepository.save(node);

        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String token = HexFormat.of().formatHex(tokenBytes);

        IrrigationAuditLog auditLog = IrrigationAuditLog.builder()
                .eventType("NODE_LIFECYCLE_TRANSITION")
                .actor("SYSTEM")
                .entityType("EdgeNode")
                .entityId(String.valueOf(id))
                .payloadJson("{\"action\":\"COMMISSION\",\"nodeId\":\"" + saved.getNodeId() + "\"}")
                .build();
        auditLogRepository.save(auditLog);

        return CommissionNodeResponse.builder()
                .id(saved.getId())
                .nodeId(saved.getNodeId())
                .lifecycleState(saved.getLifecycleState())
                .commissionedAt(LocalDateTime.now())
                .singleUseCommissioningToken(token)
                .tokenExpiresAt(LocalDateTime.now().plusHours(24))
                .build();
    }

    @Override
    public EdgeNodeResponse decommissionNode(Long id) {
        EdgeNode node = findNodeOrThrow(id);
        node.setLifecycleState(NodeLifecycleState.DECOMMISSIONED);

        List<MonitoringPoint> points = monitoringPointRepository.findByEdgeNodeId(id);
        points.forEach(p -> p.setEdgeNode(null));
        monitoringPointRepository.saveAll(points);

        EdgeNode saved = edgeNodeRepository.save(node);

        IrrigationAuditLog auditLog = IrrigationAuditLog.builder()
                .eventType("NODE_LIFECYCLE_TRANSITION")
                .actor("SYSTEM")
                .entityType("EdgeNode")
                .entityId(String.valueOf(id))
                .payloadJson("{\"action\":\"DECOMMISSION\",\"nodeId\":\"" + saved.getNodeId() + "\"}")
                .build();
        auditLogRepository.save(auditLog);

        return DtoMapper.toEdgeNodeResponse(saved);
    }

    @Override
    public EdgeNodeResponse replaceNode(Long faultyNodeId, ReplaceNodeRequest request) {
        EdgeNode faultyNode = findNodeOrThrow(faultyNodeId);

        if (request.getReplacementNodeId() == null) {
            throw new ValidationException("Replacement node ID is required");
        }

        EdgeNode replacementNode = findNodeOrThrow(request.getReplacementNodeId());

        if (replacementNode.getLifecycleState() == NodeLifecycleState.DECOMMISSIONED || replacementNode.getLifecycleState() == NodeLifecycleState.REPLACED) {
            throw new ValidationException("Replacement node cannot be in decommissioned or replaced state");
        }

        if (Boolean.TRUE.equals(request.getTransferMonitoringPoints()) || request.getTransferMonitoringPoints() == null) {
            List<MonitoringPoint> points = monitoringPointRepository.findByEdgeNodeId(faultyNodeId);
            points.forEach(p -> p.setEdgeNode(replacementNode));
            monitoringPointRepository.saveAll(points);
        }

        Optional<AutoIrrigationConfig> configOpt = autoIrrigationConfigRepository.findByEdgeNodeId(faultyNodeId);
        if (configOpt.isPresent()) {
            AutoIrrigationConfig config = configOpt.get();
            config.setEdgeNode(replacementNode);
            autoIrrigationConfigRepository.save(config);
        }

        if (faultyNode.getMonitoringZone() != null) {
            replacementNode.setMonitoringZone(faultyNode.getMonitoringZone());
        }

        faultyNode.setLifecycleState(NodeLifecycleState.REPLACED);
        replacementNode.setLifecycleState(NodeLifecycleState.COMMISSIONED);

        edgeNodeRepository.save(faultyNode);
        EdgeNode savedReplacement = edgeNodeRepository.save(replacementNode);

        IrrigationAuditLog auditLog = IrrigationAuditLog.builder()
                .eventType("NODE_LIFECYCLE_TRANSITION")
                .actor("SYSTEM")
                .entityType("EdgeNode")
                .entityId(String.valueOf(faultyNodeId))
                .payloadJson("{\"action\":\"REPLACE\",\"faultyNodeId\":\"" + faultyNode.getNodeId() + "\",\"replacementNodeId\":\"" + replacementNode.getNodeId() + "\"}")
                .build();
        auditLogRepository.save(auditLog);

        return DtoMapper.toEdgeNodeResponse(savedReplacement);
    }

    @Override
    public EdgeNodeResponse updateTransmissionConfig(Long id, UpdateTransmissionRequest request) {
        EdgeNode node = findNodeOrThrow(id);
        validateNodeActiveForOperations(id);

        EdgeNode updated = edgeNodeRepository.save(node);
        return DtoMapper.toEdgeNodeResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public NodeHealthResponse getNodeHealth(Long id) {
        EdgeNode node = findNodeOrThrow(id);
        NodeHealthMetrics metrics = node.getHealthMetrics();

        return NodeHealthResponse.builder()
                .id(node.getId())
                .nodeId(node.getNodeId())
                .healthState(node.getHealthState())
                .batteryLevel(metrics != null ? metrics.getBatteryLevel() : null)
                .solarVoltage(metrics != null ? metrics.getSolarVoltage() : null)
                .signalDbm(metrics != null ? metrics.getSignalDbm() : null)
                .snr(metrics != null ? metrics.getSnr() : null)
                .consecutiveFailures(metrics != null ? metrics.getConsecutiveFailures() : null)
                .isTelemetryStale(metrics != null ? metrics.getIsTelemetryStale() : null)
                .lastTelemetryAt(metrics != null ? metrics.getLastTelemetryAt() : null)
                .lastHeartbeat(metrics != null ? metrics.getLastHeartbeat() : null)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public NodeHealthSummaryResponse getNodeHealthSummary() {
        List<EdgeNode> nodes = edgeNodeRepository.findAll();
        List<NodeHealthResponse> responses = nodes.stream()
                .map(n -> getNodeHealth(n.getId()))
                .toList();

        int healthy = 0, degraded = 0, critical = 0, offline = 0, stale = 0;
        for (NodeHealthResponse res : responses) {
            if (res.getHealthState() == HealthState.HEALTHY) healthy++;
            else if (res.getHealthState() == HealthState.DEGRADED) degraded++;
            else if (res.getHealthState() == HealthState.CRITICAL) critical++;
            else if (res.getHealthState() == HealthState.OFFLINE) offline++;

            if (Boolean.TRUE.equals(res.getIsTelemetryStale())) stale++;
        }

        return NodeHealthSummaryResponse.builder()
                .totalNodes(responses.size())
                .healthyNodesCount(healthy)
                .degradedNodesCount(degraded)
                .criticalNodesCount(critical)
                .offlineNodesCount(offline)
                .staleTelemetryNodesCount(stale)
                .nodes(responses)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TelemetryReadingResponse> getLatestTelemetry(Long id) {
        findNodeOrThrow(id);
        List<TelemetryReading> readings = telemetryReadingRepository.findByEdgeNodeIdOrderByTimestampDesc(id);
        return readings.stream()
                .map(DtoMapper::toTelemetryReadingResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TelemetryReadingResponse> getHistoricalTelemetry(Long id, Pageable pageable) {
        findNodeOrThrow(id);
        Page<TelemetryReading> page = telemetryReadingRepository.findByEdgeNodeId(id, pageable);
        return page.map(DtoMapper::toTelemetryReadingResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public void validateNodeActiveForOperations(Long id) {
        EdgeNode node = findNodeOrThrow(id);
        checkActiveState(node);
    }

    @Override
    @Transactional(readOnly = true)
    public void validateNodeActiveForOperations(String nodeId) {
        EdgeNode node = edgeNodeRepository.findByNodeId(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Edge node not found with nodeId: " + nodeId));
        checkActiveState(node);
    }

    private EdgeNode findNodeOrThrow(Long id) {
        return edgeNodeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Edge node not found with id: " + id));
    }

    private void checkActiveState(EdgeNode node) {
        NodeLifecycleState state = node.getLifecycleState();
        if (state == NodeLifecycleState.UNREGISTERED || state == NodeLifecycleState.DECOMMISSIONED || state == NodeLifecycleState.REPLACED) {
            throw new ValidationException("NODE_NOT_ACTIVE: Node is in inactive lifecycle state: " + state);
        }
    }
}

