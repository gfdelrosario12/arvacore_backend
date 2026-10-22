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
@Transactional
@Slf4j
public class EdgeNodeRegistryServiceImpl implements EdgeNodeRegistryService {

    private final EdgeNodeRepository edgeNodeRepository;
    private final MonitoringZoneRepository monitoringZoneRepository;
    private final MonitoringPointRepository monitoringPointRepository;
    private final TelemetryReadingRepository telemetryReadingRepository;
    private final AutoIrrigationConfigRepository autoIrrigationConfigRepository;
    private final IrrigationAuditLogRepository auditLogRepository;
    private final NodeAssignmentHistoryRepository nodeAssignmentHistoryRepository;
    private final FieldRepository fieldRepository;
    private final FarmlandValidationService farmlandValidationService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.aquaflow.backend.infrastructure.event.SystemEventPublisher systemEventPublisher;

    @org.springframework.beans.factory.annotation.Autowired
    public EdgeNodeRegistryServiceImpl(
            EdgeNodeRepository edgeNodeRepository,
            MonitoringZoneRepository monitoringZoneRepository,
            MonitoringPointRepository monitoringPointRepository,
            TelemetryReadingRepository telemetryReadingRepository,
            AutoIrrigationConfigRepository autoIrrigationConfigRepository,
            IrrigationAuditLogRepository auditLogRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) NodeAssignmentHistoryRepository nodeAssignmentHistoryRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) FieldRepository fieldRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) @org.springframework.context.annotation.Lazy FarmlandValidationService farmlandValidationService) {
        this.edgeNodeRepository = edgeNodeRepository;
        this.monitoringZoneRepository = monitoringZoneRepository;
        this.monitoringPointRepository = monitoringPointRepository;
        this.telemetryReadingRepository = telemetryReadingRepository;
        this.autoIrrigationConfigRepository = autoIrrigationConfigRepository;
        this.auditLogRepository = auditLogRepository;
        this.nodeAssignmentHistoryRepository = nodeAssignmentHistoryRepository;
        this.fieldRepository = fieldRepository;
        this.farmlandValidationService = farmlandValidationService;
    }

    public EdgeNodeRegistryServiceImpl(
            EdgeNodeRepository edgeNodeRepository,
            MonitoringZoneRepository monitoringZoneRepository,
            MonitoringPointRepository monitoringPointRepository,
            TelemetryReadingRepository telemetryReadingRepository,
            AutoIrrigationConfigRepository autoIrrigationConfigRepository,
            IrrigationAuditLogRepository auditLogRepository) {
        this(edgeNodeRepository, monitoringZoneRepository, monitoringPointRepository, telemetryReadingRepository,
             autoIrrigationConfigRepository, auditLogRepository, null, null, null);
    }

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

        List<EdgeNode> visibleNodes = nodes.stream()
                .filter(n -> lifecycleState != null
                        ? n.getLifecycleState() == lifecycleState
                        : (n.getLifecycleState() != NodeLifecycleState.UNREGISTERED &&
                           n.getLifecycleState() != NodeLifecycleState.DECOMMISSIONED))
                .filter(n -> healthState == null || n.getHealthState() == healthState)
                .filter(n -> zoneId == null || (n.getMonitoringZone() != null && Objects.equals(n.getMonitoringZone().getId(), zoneId)))
                .toList();

        // Resolve the latest reading of every node with a single batched query instead of
        // loading the complete telemetry history of every node one by one.
        Map<Long, List<TelemetryReading>> latestByNode = groupLatestReadingsByNode(
                visibleNodes.stream().map(EdgeNode::getId).toList());

        return visibleNodes.stream()
                .map(n -> DtoMapper.toEdgeNodeResponse(n, latestByNode.getOrDefault(n.getId(), List.of())))
                .collect(Collectors.toList());
    }

    /**
     * Resolves the newest reading of every sensor type for the supplied nodes with a single
     * database statement.
     *
     * @param edgeNodeIds identifiers of the nodes to resolve
     * @return a map of node identifier to its newest readings
     */
    private Map<Long, List<TelemetryReading>> groupLatestReadingsByNode(List<Long> edgeNodeIds) {
        if (edgeNodeIds == null || edgeNodeIds.isEmpty()) {
            return Map.of();
        }
        List<TelemetryReading> readings = telemetryReadingRepository.findLatestReadingsByEdgeNodeIds(edgeNodeIds);
        Map<Long, List<TelemetryReading>> grouped = new LinkedHashMap<>();
        for (TelemetryReading reading : readings) {
            if (reading.getEdgeNode() == null) continue;
            Long nodeId = reading.getEdgeNode().getId();
            List<TelemetryReading> bucket = grouped.computeIfAbsent(nodeId, k -> new ArrayList<>());
            // Guard against duplicate rows sharing the exact same maximum timestamp.
            boolean duplicate = bucket.stream().anyMatch(existing ->
                    existing.getSensorType() == reading.getSensorType()
                            && Objects.equals(existing.getTimestamp(), reading.getTimestamp()));
            if (!duplicate) {
                bucket.add(reading);
            }
        }
        return grouped;
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
        Map<Long, List<TelemetryReading>> latestByNode = groupLatestReadingsByNode(List.of(id));
        return DtoMapper.toEdgeNodeResponse(node, latestByNode.getOrDefault(id, List.of()));
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
    public EdgeNodeResponse assignNodeToField(String idOrNodeId, AssignNodeFieldRequest request) {
        EdgeNode node = findNodeOrThrow(idOrNodeId);
        return doAssignNodeToField(node, request);
    }

    @Override
    public EdgeNodeResponse assignNodeToField(Long id, AssignNodeFieldRequest request) {
        EdgeNode node = findNodeOrThrow(id);
        return doAssignNodeToField(node, request);
    }

    private EdgeNodeResponse doAssignNodeToField(EdgeNode node, AssignNodeFieldRequest request) {
        if (node.getLifecycleState() == NodeLifecycleState.DECOMMISSIONED ||
            node.getLifecycleState() == NodeLifecycleState.REPLACED ||
            node.getLifecycleState() == NodeLifecycleState.UNREGISTERED) {
            throw new ValidationException("Cannot assign node in inactive state: " + node.getLifecycleState());
        }

        Long previousZoneId = node.getMonitoringZone() != null ? node.getMonitoringZone().getId() : null;
        Long previousFieldId = (node.getMonitoringZone() != null && node.getMonitoringZone().getField() != null)
                ? node.getMonitoringZone().getField().getId() : null;

        // Close any active assignment history record for this node
        var activeHistoryOpt = nodeAssignmentHistoryRepository.findActiveAssignment(node.getId());
        if (activeHistoryOpt.isPresent()) {
            NodeAssignmentHistory active = activeHistoryOpt.get();
            active.setUnassignedAt(LocalDateTime.now());
            nodeAssignmentHistoryRepository.save(active);
        }

        if (request.getMonitoringZoneId() != null) {
            MonitoringZone zone = monitoringZoneRepository.findById(request.getMonitoringZoneId())
                    .orElseThrow(() -> new ResourceNotFoundException("Monitoring zone not found: " + request.getMonitoringZoneId()));

            if (Boolean.TRUE.equals(zone.getIsArchived())) {
                throw new ValidationException("Cannot assign node to an archived monitoring zone: " + zone.getName());
            }

            if (request.getFieldId() != null && zone.getField() != null) {
                if (!request.getFieldId().equals(zone.getField().getId())) {
                    throw new ValidationException(String.format("Monitoring zone %d belongs to field %d, not requested field %d",
                            zone.getId(), zone.getField().getId(), request.getFieldId()));
                }
            }

            // Enforce strict 1 Zone = 1 Node maximum constraint
            List<EdgeNode> existingAssigned = edgeNodeRepository.findByMonitoringZoneId(zone.getId());
            for (EdgeNode existing : existingAssigned) {
                if (!existing.getId().equals(node.getId()) &&
                    existing.getLifecycleState() != NodeLifecycleState.UNREGISTERED &&
                    existing.getLifecycleState() != NodeLifecycleState.DECOMMISSIONED &&
                    existing.getLifecycleState() != NodeLifecycleState.REPLACED) {
                    throw new ValidationException(String.format(
                            "Zone '%s' (ID: %d) already has node '%s' assigned. Enforcing 1 Zone = 1 Node constraint.",
                            zone.getName(), zone.getId(), existing.getNodeId()), "ZONE_ALREADY_ASSIGNED");
                }
            }

            node.setMonitoringZone(zone);
            EdgeNode saved = edgeNodeRepository.save(node);

            // Record assignment in history
            Long targetFieldId = zone.getField() != null ? zone.getField().getId() : null;
            NodeAssignmentHistory history = NodeAssignmentHistory.builder()
                    .edgeNodeId(saved.getId())
                    .nodeId(saved.getNodeId())
                    .fieldId(targetFieldId)
                    .monitoringZoneId(zone.getId())
                    .assignedAt(LocalDateTime.now())
                    .assignedBy("ARVALINK_OPERATOR")
                    .reason("Farmland layout assignment")
                    .build();
            nodeAssignmentHistoryRepository.save(history);

            // Ensure a monitoring point exists in this zone for the node
            List<MonitoringPoint> points = monitoringPointRepository.findByEdgeNodeId(saved.getId());
            if (points.isEmpty()) {
                MonitoringPoint point = MonitoringPoint.builder()
                        .name("MP-" + saved.getNodeId())
                        .monitoringZone(zone)
                        .edgeNode(saved)
                        .primarySensorType(SensorType.SOIL_MOISTURE)
                        .depthCm(15.0)
                        .build();
                monitoringPointRepository.save(point);
            } else {
                for (MonitoringPoint p : points) {
                    p.setMonitoringZone(zone);
                    monitoringPointRepository.save(p);
                }
            }

            // Audit log
            IrrigationAuditLog auditLog = IrrigationAuditLog.builder()
                    .eventType("NODE_FIELD_ASSIGNMENT_CHANGED")
                    .actor("ARVALINK_OPERATOR")
                    .entityType("EdgeNode")
                    .entityId(String.valueOf(node.getId()))
                    .payloadJson(String.format("{\"nodeId\":\"%s\",\"previousZoneId\":%s,\"newZoneId\":%d,\"fieldId\":%d}",
                            saved.getNodeId(), previousZoneId != null ? previousZoneId : "null", zone.getId(),
                            targetFieldId))
                    .build();
            auditLogRepository.save(auditLog);

            if (farmlandValidationService != null && targetFieldId != null) {
                try {
                    farmlandValidationService.updateFarmlandOperationalStatus(targetFieldId);
                } catch (Exception ex) {
                    log.warn("Could not update operational status for field {}: {}", targetFieldId, ex.getMessage());
                }
            }

            log.info("Node {} successfully assigned to field {} and zone {}",
                    saved.getNodeId(), zone.getField() != null ? zone.getField().getName() : "N/A", zone.getName());
            if (systemEventPublisher != null) {
                try {
                    systemEventPublisher.publishNodeStatusChanged(
                            saved.getNodeId(),
                            targetFieldId,
                            previousZoneId != null ? "ZONE_" + previousZoneId : "UNASSIGNED",
                            "ZONE_" + zone.getId()
                    );
                } catch (Exception ignored) {}
            }
            return DtoMapper.toEdgeNodeResponse(saved, telemetryReadingRepository.findByEdgeNodeIdOrderByTimestampDesc(saved.getId()));
        } else {
            // Unassign node from current zone
            node.setMonitoringZone(null);
            List<MonitoringPoint> points = monitoringPointRepository.findByEdgeNodeId(node.getId());
            for (MonitoringPoint p : points) {
                p.setEdgeNode(null);
                monitoringPointRepository.save(p);
            }

            EdgeNode saved = edgeNodeRepository.save(node);

            IrrigationAuditLog auditLog = IrrigationAuditLog.builder()
                    .eventType("NODE_FIELD_ASSIGNMENT_CLEARED")
                    .actor("ARVALINK_OPERATOR")
                    .entityType("EdgeNode")
                    .entityId(String.valueOf(node.getId()))
                    .payloadJson(String.format("{\"nodeId\":\"%s\",\"previousZoneId\":%s,\"newZoneId\":null}",
                            saved.getNodeId(), previousZoneId != null ? previousZoneId : "null"))
                    .build();
            auditLogRepository.save(auditLog);

            if (farmlandValidationService != null && previousFieldId != null) {
                try {
                    farmlandValidationService.updateFarmlandOperationalStatus(previousFieldId);
                } catch (Exception ex) {
                    log.warn("Could not update operational status for field {}: {}", previousFieldId, ex.getMessage());
                }
            }

            log.info("Node {} unassigned from previous zone {}", saved.getNodeId(), previousZoneId);
            if (systemEventPublisher != null) {
                try {
                    systemEventPublisher.publishNodeStatusChanged(
                            saved.getNodeId(),
                            previousFieldId,
                            previousZoneId != null ? "ZONE_" + previousZoneId : "ASSIGNED",
                            "UNASSIGNED"
                    );
                } catch (Exception ignored) {}
            }
            return DtoMapper.toEdgeNodeResponse(saved, telemetryReadingRepository.findByEdgeNodeIdOrderByTimestampDesc(saved.getId()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<NodeAssignmentHistoryResponse> getNodeAssignmentHistory(Long id) {
        EdgeNode node = findNodeOrThrow(id);
        return doFetchNodeAssignmentHistory(node);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NodeAssignmentHistoryResponse> getNodeAssignmentHistory(String nodeId) {
        EdgeNode node = findNodeOrThrow(nodeId);
        return doFetchNodeAssignmentHistory(node);
    }

    private List<NodeAssignmentHistoryResponse> doFetchNodeAssignmentHistory(EdgeNode node) {
        List<NodeAssignmentHistory> histories = nodeAssignmentHistoryRepository.findByEdgeNodeIdOrderByAssignedAtDesc(node.getId());
        List<NodeAssignmentHistoryResponse> responses = new ArrayList<>();
        for (NodeAssignmentHistory h : histories) {
            String fieldName = null;
            if (h.getFieldId() != null) {
                fieldName = fieldRepository.findById(h.getFieldId()).map(Field::getName).orElse(null);
            }
            String zoneName = null;
            String zoneCode = null;
            if (h.getMonitoringZoneId() != null) {
                var zOpt = monitoringZoneRepository.findById(h.getMonitoringZoneId());
                if (zOpt.isPresent()) {
                    zoneName = zOpt.get().getName();
                    zoneCode = zOpt.get().getCode();
                }
            }
            responses.add(DtoMapper.toNodeAssignmentHistoryResponse(h, fieldName, zoneName, zoneCode));
        }
        return responses;
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
        if (systemEventPublisher != null) {
            try {
                Long fieldId = (updated.getMonitoringZone() != null && updated.getMonitoringZone().getField() != null)
                        ? updated.getMonitoringZone().getField().getId() : null;
                systemEventPublisher.publishNodeStatusChanged(
                        updated.getNodeId(),
                        fieldId,
                        "CONFIG_ACTIVE",
                        "TRANSMISSION_INTERVAL_" + (request != null ? request.getIntervalSeconds() : 3) + "S"
                );
            } catch (Exception ignored) {}
        }
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
        // Only the newest reading of every sensor type is returned: the endpoint is named
        // "latest" and the previous implementation streamed the complete telemetry history
        // of the node to the client.
        Map<Long, List<TelemetryReading>> latestByNode = groupLatestReadingsByNode(List.of(id));
        return latestByNode.getOrDefault(id, List.of()).stream()
                .sorted(Comparator.comparing(TelemetryReading::getTimestamp,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .map(DtoMapper::toTelemetryReadingResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public NodeLatestTelemetrySnapshotResponse getLatestTelemetrySnapshot(Collection<String> requestedNodeIds) {
        List<EdgeNode> nodes = edgeNodeRepository.findAll();

        Map<String, EdgeNode> byNodeId = new LinkedHashMap<>();
        Map<String, EdgeNode> byNumericId = new LinkedHashMap<>();
        for (EdgeNode node : nodes) {
            byNodeId.put(node.getNodeId(), node);
            byNumericId.put(String.valueOf(node.getId()), node);
        }

        List<EdgeNode> scoped;
        if (requestedNodeIds == null || requestedNodeIds.isEmpty()) {
            scoped = nodes.stream()
                    .filter(n -> n.getLifecycleState() != NodeLifecycleState.UNREGISTERED
                            && n.getLifecycleState() != NodeLifecycleState.DECOMMISSIONED)
                    .toList();
        } else {
            List<EdgeNode> resolved = new ArrayList<>();
            for (String requested : requestedNodeIds) {
                if (requested == null || requested.isBlank()) continue;
                EdgeNode node = byNodeId.get(requested.trim());
                if (node == null) {
                    node = byNumericId.get(requested.trim());
                }
                if (node == null) {
                    throw new ResourceNotFoundException("Edge node not found with identifier: " + requested);
                }
                // Only nodes the caller may observe are exposed; lifecycle state stays
                // independent from the telemetry state.
                if (node.getLifecycleState() == NodeLifecycleState.UNREGISTERED
                        || node.getLifecycleState() == NodeLifecycleState.DECOMMISSIONED) {
                    continue;
                }
                resolved.add(node);
            }
            scoped = resolved;
        }

        Map<Long, List<TelemetryReading>> latestByNode = groupLatestReadingsByNode(
                scoped.stream().map(EdgeNode::getId).toList());

        List<NodeLatestTelemetryResponse> snapshots = scoped.stream()
                .map(n -> DtoMapper.toNodeLatestTelemetryResponse(n, latestByNode.getOrDefault(n.getId(), List.of())))
                .collect(Collectors.toList());

        return NodeLatestTelemetrySnapshotResponse.builder()
                .serverTime(LocalDateTime.now())
                .nodes(snapshots)
                .build();
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

    private EdgeNode findNodeOrThrow(String idOrNodeId) {
        if (idOrNodeId == null || idOrNodeId.isBlank()) {
            throw new ResourceNotFoundException("Node ID cannot be null or empty");
        }
        try {
            Long numericId = Long.parseLong(idOrNodeId);
            var opt = edgeNodeRepository.findById(numericId);
            if (opt.isPresent()) return opt.get();
        } catch (NumberFormatException ignored) {}
        return edgeNodeRepository.findByNodeId(idOrNodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Edge node not found with identifier: " + idOrNodeId));
    }

    private void checkActiveState(EdgeNode node) {
        NodeLifecycleState state = node.getLifecycleState();
        if (state == NodeLifecycleState.UNREGISTERED || state == NodeLifecycleState.DECOMMISSIONED || state == NodeLifecycleState.REPLACED) {
            throw new ValidationException("NODE_NOT_ACTIVE: Node is in inactive lifecycle state: " + state);
        }
    }
}

