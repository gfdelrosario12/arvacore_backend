package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.request.MasterStateUpdateRequest;
import com.aquaflow.backend.dto.request.NodePairingSubmissionRequest;
import com.aquaflow.backend.dto.request.PairingRejectionRequest;
import com.aquaflow.backend.dto.request.RegisterNodeRequest;
import com.aquaflow.backend.dto.response.*;
import com.aquaflow.backend.entity.*;
import com.aquaflow.backend.infrastructure.event.SystemEventPublisher;
import com.aquaflow.backend.infrastructure.exception.ResourceNotFoundException;
import com.aquaflow.backend.infrastructure.exception.ValidationException;
import com.aquaflow.backend.infrastructure.web.RealtimeEventPublisherService;
import com.aquaflow.backend.persistence.EdgeNodeRepository;
import com.aquaflow.backend.persistence.MonitoringPointRepository;
import com.aquaflow.backend.persistence.MonitoringZoneRepository;
import com.aquaflow.backend.persistence.NodePairingRequestRepository;
import com.aquaflow.backend.persistence.SystemAuditLogRepository;
import com.aquaflow.backend.persistence.SystemStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class NodePairingServiceImpl implements NodePairingService {

    private static final String MASTER_KEY = "SYSTEM";

    private final SystemStateRepository systemStateRepository;
    private final NodePairingRequestRepository pairingRequestRepository;
    private final EdgeNodeRepository edgeNodeRepository;
    private final EdgeNodeRegistryService edgeNodeRegistryService;
    private final SystemAuditLogRepository auditLogRepository;
    private final SystemEventPublisher systemEventPublisher;

    @Autowired(required = false)
    private RealtimeEventPublisherService realtimeEventPublisherService;

    @Autowired(required = false)
    private MonitoringZoneRepository monitoringZoneRepository;

    @Autowired(required = false)
    private MonitoringPointRepository monitoringPointRepository;

    @Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private TelemetryIngestionService telemetryIngestionService;

    // -------------------------------------------------------------
    // Master State Management
    // -------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public MasterStateResponse getMasterState() {
        SystemState state = getOrCreateMasterState();
        return toMasterStateResponse(state);
    }

    @Override
    public MasterStateResponse updateMasterState(MasterStateUpdateRequest request, String actor) {
        SystemState state = getOrCreateMasterState();
        boolean previous = state.isMasterEnabled();
        boolean next = request.getEnabled();

        state.setMasterEnabled(next);
        state.setUpdatedBy(actor != null ? actor : "SYSTEM");
        state.setNotes(request.getNotes());
        state.setUpdatedAt(LocalDateTime.now());
        SystemState saved = systemStateRepository.save(state);

        // Audit log
        auditLogRepository.save(SystemAuditLog.builder()
                .eventType("MASTER_STATE_CHANGED")
                .actor(actor != null ? actor : "SYSTEM")
                .entityType("SystemState")
                .entityId(MASTER_KEY)
                .previousState(String.valueOf(previous))
                .resultingState(String.valueOf(next))
                .payloadJson(String.format("{\"notes\":\"%s\"}", request.getNotes() != null ? request.getNotes() : ""))
                .createdAt(LocalDateTime.now())
                .build());

        // Event notification
        try {
            systemEventPublisher.publishMasterStateChanged(next, actor);
        } catch (Exception e) {
            log.warn("Failed to publish master state event: {}", e.getMessage());
        }

        return toMasterStateResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isMasterEnabled() {
        return getOrCreateMasterState().isMasterEnabled();
    }

    private SystemState getOrCreateMasterState() {
        return systemStateRepository.findById(MASTER_KEY)
                .orElseGet(() -> systemStateRepository.save(SystemState.builder()
                        .stateKey(MASTER_KEY)
                        .masterEnabled(true)
                        .updatedBy("SYSTEM")
                        .notes("Default initialized master state")
                        .updatedAt(LocalDateTime.now())
                        .build()));
    }

    private MasterStateResponse toMasterStateResponse(SystemState state) {
        return MasterStateResponse.builder()
                .masterEnabled(state.isMasterEnabled())
                .updatedBy(state.getUpdatedBy())
                .notes(state.getNotes())
                .updatedAt(state.getUpdatedAt())
                .build();
    }

    // -------------------------------------------------------------
    // Node Pairing Workflow
    // -------------------------------------------------------------
    @Override
    public PairingRequestResponse submitPairingRequest(NodePairingSubmissionRequest request) {
        if (!isMasterEnabled()) {
            throw new ValidationException("MASTER_OFF: Master system is turned OFF. Node pairing requests are disallowed.");
        }

        String nodeId = request.getNodeId().trim();

        // 1. Check if node is already recognized in EdgeNode registry to update metadata
        Optional<EdgeNode> existingNode = edgeNodeRepository.findByNodeId(nodeId);
        if (existingNode.isPresent()) {
            EdgeNode node = existingNode.get();
            if (request.getMacAddress() != null && node.getIdentity() != null) {
                node.getIdentity().setMacAddress(request.getMacAddress());
                node.getIdentity().setIdentityValue(request.getMacAddress());
            }
            if (request.getHardwareModel() != null) node.setHardwareModel(request.getHardwareModel());
            if (request.getFirmwareVersion() != null) node.setFirmwareVersion(request.getFirmwareVersion());
            if (node.getHealthMetrics() != null) {
                node.getHealthMetrics().setLastHeartbeat(LocalDateTime.now());
            }
            edgeNodeRepository.save(node);
        }

        // 2. Manage PENDING pairing request with 30-minute expiration window (deduplication)
        Optional<NodePairingRequest> existingPending = pairingRequestRepository
                .findFirstByNodeIdAndPairingStatusOrderByRequestedAtDesc(nodeId, PairingStatus.PENDING);

        NodePairingRequest entity;
        LocalDateTime now = LocalDateTime.now();
        if (existingPending.isPresent()) {
            NodePairingRequest pending = existingPending.get();
            if (pending.isExpired()) {
                pending.setPairingStatus(PairingStatus.EXPIRED);
                pending.setProcessedAt(now);
                pending.setProcessedBy("SYSTEM_EXPIRATION");
                pairingRequestRepository.save(pending);

                // Expired request: allow fresh pairing request with new 30-minute expiration window
                entity = NodePairingRequest.builder()
                        .nodeId(nodeId)
                        .macAddress(request.getMacAddress())
                        .hardwareModel(request.getHardwareModel())
                        .firmwareVersion(request.getFirmwareVersion())
                        .pairingStatus(PairingStatus.PENDING)
                        .requestedAt(now)
                        .expiresAt(now.plusMinutes(30))
                        .build();
                entity = pairingRequestRepository.save(entity);
                log.info("Previous pairing request expired; created fresh PENDING request for node {}", nodeId);
            } else {
                entity = pending;
                // Active window: update hardware/firmware metadata WITHOUT resetting expiresAt
                if (request.getMacAddress() != null) entity.setMacAddress(request.getMacAddress());
                if (request.getHardwareModel() != null) entity.setHardwareModel(request.getHardwareModel());
                if (request.getFirmwareVersion() != null) entity.setFirmwareVersion(request.getFirmwareVersion());
                entity = pairingRequestRepository.save(entity);
                log.info("Updated existing PENDING pairing request for node {} without extending expiration (expires at {})", nodeId, entity.getExpiresAt());
            }
        } else {
            entity = NodePairingRequest.builder()
                    .nodeId(nodeId)
                    .macAddress(request.getMacAddress())
                    .hardwareModel(request.getHardwareModel())
                    .firmwareVersion(request.getFirmwareVersion())
                    .pairingStatus(PairingStatus.PENDING)
                    .requestedAt(now)
                    .expiresAt(now.plusMinutes(30))
                    .build();
            entity = pairingRequestRepository.save(entity);
            log.info("Created new PENDING pairing request for node {} (expires at {})", nodeId, entity.getExpiresAt());
        }

        // Audit log
        auditLogRepository.save(SystemAuditLog.builder()
                .eventType("NODE_PAIRING_REQUESTED")
                .actor(nodeId)
                .entityType("NodePairingRequest")
                .entityId(String.valueOf(entity.getId()))
                .previousState("NONE")
                .resultingState("PENDING")
                .payloadJson(String.format("{\"nodeId\":\"%s\",\"mac\":\"%s\"}", nodeId, request.getMacAddress()))
                .createdAt(LocalDateTime.now())
                .build());

        // Notify realtime subscribers
        try {
            systemEventPublisher.publishPairingRequestUpdated(nodeId, "PENDING");
        } catch (Exception e) {
            log.warn("Failed to publish pairing request event: {}", e.getMessage());
        }

        return toPairingResponse(entity);
    }

    @Override
    public List<PairingRequestResponse> getPendingPairingRequests() {
        List<NodePairingRequest> pendingList = pairingRequestRepository.findByPairingStatusOrderByRequestedAtDesc(PairingStatus.PENDING);
        List<PairingRequestResponse> active = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (NodePairingRequest req : pendingList) {
            if (req.isExpired()) {
                req.setPairingStatus(PairingStatus.EXPIRED);
                req.setProcessedAt(now);
                req.setProcessedBy("SYSTEM_EXPIRATION");
                pairingRequestRepository.save(req);
            } else {
                active.add(toPairingResponse(req));
            }
        }
        return active;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PairingRequestResponse> getAllPairingRequests() {
        return pairingRequestRepository.findAll()
                .stream()
                .map(this::toPairingResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PairingRequestResponse getPairingRequest(Long id) {
        NodePairingRequest req = pairingRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pairing request not found: " + id));
        return toPairingResponse(req);
    }

    @Override
    @Transactional(readOnly = true)
    public PairingRequestResponse getPairingStatusByNodeId(String nodeId) {
        String cleanNodeId = (nodeId != null) ? nodeId.trim() : "";
        Optional<EdgeNode> existingNode = edgeNodeRepository.findByNodeId(cleanNodeId);
        Optional<NodePairingRequest> latestReq = pairingRequestRepository
                .findFirstByNodeIdOrderByRequestedAtDesc(cleanNodeId);

        if (existingNode.isPresent()) {
            EdgeNode node = existingNode.get();
            // If the node lifecycle is UNREGISTERED, return REVOKED status
            if (node.getLifecycleState() == NodeLifecycleState.UNREGISTERED) {
                if (latestReq.isPresent()) {
                    return toPairingResponse(latestReq.get());
                }
                return PairingRequestResponse.builder()
                        .nodeId(cleanNodeId)
                        .hardwareModel(node.getHardwareModel())
                        .firmwareVersion(node.getFirmwareVersion())
                        .macAddress(node.getIdentity() != null ? node.getIdentity().getMacAddress() : null)
                        .pairingStatus(PairingStatus.REVOKED)
                        .recognized(false)
                        .existingEdgeNodeId(node.getId())
                        .existingLifecycleState(node.getLifecycleState() != null ? node.getLifecycleState().name() : null)
                        .build();
            }

            if (latestReq.isPresent()) {
                return toPairingResponse(latestReq.get());
            }
            return PairingRequestResponse.builder()
                    .nodeId(cleanNodeId)
                    .hardwareModel(node.getHardwareModel())
                    .firmwareVersion(node.getFirmwareVersion())
                    .macAddress(node.getIdentity() != null ? node.getIdentity().getMacAddress() : null)
                    .pairingStatus(PairingStatus.APPROVED)
                    .recognized(true)
                    .existingEdgeNodeId(node.getId())
                    .existingLifecycleState(node.getLifecycleState() != null ? node.getLifecycleState().name() : null)
                    .existingZoneId(node.getMonitoringZone() != null ? node.getMonitoringZone().getId() : null)
                    .existingZoneName(node.getMonitoringZone() != null ? node.getMonitoringZone().getName() : null)
                    .build();
        }

        if (latestReq.isPresent()) {
            return toPairingResponse(latestReq.get());
        }

        throw new ResourceNotFoundException("No pairing record or node found with nodeId: " + cleanNodeId);
    }

    @Override
    public PairingRequestResponse rejectPairingRequest(Long id, PairingRejectionRequest request, String actor) {
        NodePairingRequest req = pairingRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pairing request not found: " + id));

        req.setPairingStatus(PairingStatus.REJECTED);
        req.setRejectionReason(request != null ? request.getReason() : "Rejected by administrator");
        req.setProcessedAt(LocalDateTime.now());
        req.setProcessedBy(actor != null ? actor : "ADMIN");
        NodePairingRequest saved = pairingRequestRepository.save(req);

        // Audit log
        auditLogRepository.save(SystemAuditLog.builder()
                .eventType("NODE_PAIRING_REJECTED")
                .actor(actor != null ? actor : "ADMIN")
                .entityType("NodePairingRequest")
                .entityId(String.valueOf(id))
                .previousState("PENDING")
                .resultingState("REJECTED")
                .payloadJson(String.format("{\"reason\":\"%s\"}", req.getRejectionReason()))
                .createdAt(LocalDateTime.now())
                .build());

        try {
            systemEventPublisher.publishPairingRequestUpdated(req.getNodeId(), "REJECTED");
        } catch (Exception e) {
            log.warn("Failed to publish pairing reject event: {}", e.getMessage());
        }

        return toPairingResponse(saved);
    }

    @Override
    public EdgeNodeResponse approveAndAssignNode(Long pairingRequestId, RegisterNodeRequest request, String actor) {
        if (!isMasterEnabled()) {
            throw new ValidationException("MASTER_OFF: Master system is turned OFF. Node approvals are disallowed.");
        }

        NodePairingRequest pairingReq = pairingRequestRepository.findById(pairingRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Pairing request not found: " + pairingRequestId));

        if (pairingReq.isExpired() || pairingReq.getPairingStatus() == PairingStatus.REVOKED) {
            PairingStatus finalStatus = pairingReq.getPairingStatus() == PairingStatus.REVOKED ? PairingStatus.REVOKED : PairingStatus.EXPIRED;
            pairingReq.setPairingStatus(finalStatus);
            pairingReq.setProcessedAt(LocalDateTime.now());
            pairingReq.setProcessedBy(finalStatus == PairingStatus.REVOKED ? (actor != null ? actor : "ADMIN") : "SYSTEM_EXPIRATION");
            pairingRequestRepository.save(pairingReq);
            throw new ValidationException("PAIRING_EXPIRED: Pairing request has expired or has been revoked. A new pairing request must be initiated from the node.");
        }

        // Ensure pairing request is pending or can be re-approved idempotently
        pairingReq.setPairingStatus(PairingStatus.APPROVED);
        pairingReq.setProcessedAt(LocalDateTime.now());
        pairingReq.setProcessedBy(actor != null ? actor : "ADMIN");
        pairingRequestRepository.save(pairingReq);

        // Also clean up any other PENDING requests for the same node
        List<NodePairingRequest> otherPending = pairingRequestRepository
                .findByPairingStatusOrderByRequestedAtDesc(PairingStatus.PENDING)
                .stream()
                .filter(p -> !p.getId().equals(pairingRequestId) && pairingReq.getNodeId().equalsIgnoreCase(p.getNodeId()))
                .toList();
        for (NodePairingRequest p : otherPending) {
            p.setPairingStatus(PairingStatus.APPROVED);
            p.setProcessedAt(LocalDateTime.now());
            p.setProcessedBy(actor != null ? actor : "ADMIN");
            pairingRequestRepository.save(p);
        }

        // Check if edge node already exists in registry
        Optional<EdgeNode> existingOpt = edgeNodeRepository.findByNodeId(request.getNodeId());
        EdgeNodeResponse nodeResponse;
        if (existingOpt.isPresent()) {
            // Update node assignment/model/version
            EdgeNode existing = existingOpt.get();
            nodeResponse = edgeNodeRegistryService.updateNode(existing.getId(), request);
            log.info("Updated existing EdgeNode {} during pairing approval", existing.getNodeId());
        } else {
            // Register new edge node into the system
            nodeResponse = edgeNodeRegistryService.registerNode(request);
            log.info("Registered new EdgeNode {} during pairing approval", nodeResponse.getNodeId());
        }

        // Commission the node to ACTIVE state so telemetry will be authorized
        try {
            edgeNodeRegistryService.commissionNode(nodeResponse.getId(), null);
            EdgeNode commissionedNode = edgeNodeRepository.findById(nodeResponse.getId()).orElse(null);
            if (commissionedNode != null) {
                commissionedNode.setLifecycleState(NodeLifecycleState.ACTIVE);
                edgeNodeRepository.save(commissionedNode);
            }
        } catch (Exception e) {
            log.info("Node commissioning during approval status: {}", e.getMessage());
        }

        // If assigned to a monitoring zone, ensure a primary MonitoringPoint exists
        if (request.getMonitoringZoneId() != null && monitoringZoneRepository != null && monitoringPointRepository != null) {
            try {
                MonitoringZone zone = monitoringZoneRepository.findById(request.getMonitoringZoneId()).orElse(null);
                if (zone != null) {
                    List<MonitoringPoint> existingPoints = monitoringPointRepository.findByEdgeNodeId(nodeResponse.getId());
                    if (existingPoints.isEmpty()) {
                        EdgeNode nodeEntity = edgeNodeRepository.findById(nodeResponse.getId()).orElse(null);
                        if (nodeEntity != null) {
                            MonitoringPoint mp = MonitoringPoint.builder()
                                    .name("MP-" + nodeEntity.getNodeId())
                                    .monitoringZone(zone)
                                    .edgeNode(nodeEntity)
                                    .primarySensorType(SensorType.SOIL_MOISTURE)
                                    .depthCm(15.0)
                                    .build();
                            monitoringPointRepository.save(mp);
                            log.info("Created primary MonitoringPoint for newly approved node {}", nodeEntity.getNodeId());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("MonitoringPoint creation for node {}: {}", nodeResponse.getNodeId(), e.getMessage());
            }
        }

        // Audit log
        auditLogRepository.save(SystemAuditLog.builder()
                .eventType("NODE_PAIRING_APPROVED_AND_ASSIGNED")
                .actor(actor != null ? actor : "ADMIN")
                .entityType("EdgeNode")
                .entityId(String.valueOf(nodeResponse.getId()))
                .previousState("PENDING_PAIRING")
                .resultingState("ACTIVE")
                .payloadJson(String.format("{\"pairingRequestId\":%d,\"zoneId\":%s}",
                        pairingRequestId,
                        request.getMonitoringZoneId() != null ? request.getMonitoringZoneId() : "null"))
                .createdAt(LocalDateTime.now())
                .build());

        try {
            systemEventPublisher.publishPairingRequestUpdated(pairingReq.getNodeId(), "APPROVED");
        } catch (Exception e) {
            log.warn("Failed to publish pairing approve event: {}", e.getMessage());
        }

        return edgeNodeRegistryService.getNodeById(nodeResponse.getId());
    }

    @Override
    public NodeUnpairResponse unpairNode(String nodeId, String actor) {
        String cleanNodeId = (nodeId != null) ? nodeId.trim() : "";
        if (cleanNodeId.isBlank()) {
            throw new ValidationException("Node ID cannot be null or empty");
        }

        String effectiveActor = (actor != null && !actor.isBlank()) ? actor : "ADMIN";
        LocalDateTime now = LocalDateTime.now();
        String previousState = "UNREGISTERED";
        Long fieldId = null;

        // 1. Check and revoke EdgeNode if present in registry
        Optional<EdgeNode> nodeOpt = edgeNodeRepository.findByNodeId(cleanNodeId);
        if (nodeOpt.isPresent()) {
            EdgeNode node = nodeOpt.get();
            previousState = node.getLifecycleState() != null ? node.getLifecycleState().name() : "ACTIVE";
            if (node.getMonitoringZone() != null && node.getMonitoringZone().getField() != null) {
                fieldId = node.getMonitoringZone().getField().getId();
            }

            node.setLifecycleState(NodeLifecycleState.UNREGISTERED);

            // Disassociate from any monitoring points
            if (monitoringPointRepository != null) {
                try {
                    List<MonitoringPoint> points = monitoringPointRepository.findByEdgeNodeId(node.getId());
                    if (points != null && !points.isEmpty()) {
                        points.forEach(p -> p.setEdgeNode(null));
                        monitoringPointRepository.saveAll(points);
                        log.info("Disassociated {} monitoring point(s) from unpaired node {}", points.size(), cleanNodeId);
                    }
                } catch (Exception e) {
                    log.warn("Error disassociating monitoring points for node {}: {}", cleanNodeId, e.getMessage());
                }
            }

            node.setMonitoringZone(null);
            edgeNodeRepository.save(node);
            log.info("Transitioned EdgeNode {} lifecycleState to UNREGISTERED", cleanNodeId);
        }

        // 2. Revoke all pairing requests for this node (both PENDING and historical APPROVED)
        List<NodePairingRequest> nodeRequests = pairingRequestRepository.findAll()
                .stream()
                .filter(r -> cleanNodeId.equalsIgnoreCase(r.getNodeId()))
                .toList();

        for (NodePairingRequest req : nodeRequests) {
            req.setPairingStatus(PairingStatus.REVOKED);
            req.setRejectionReason("Node forcefully unpaired and revoked by " + effectiveActor);
            req.setProcessedAt(now);
            req.setProcessedBy(effectiveActor);
            pairingRequestRepository.save(req);
        }

        // If no prior pairing record existed, create an explicit REVOKED record
        if (nodeRequests.isEmpty()) {
            NodePairingRequest revokedRecord = NodePairingRequest.builder()
                    .nodeId(cleanNodeId)
                    .pairingStatus(PairingStatus.REVOKED)
                    .rejectionReason("Node forcefully unpaired and revoked by " + effectiveActor)
                    .requestedAt(now)
                    .expiresAt(now)
                    .processedAt(now)
                    .processedBy(effectiveActor)
                    .build();
            pairingRequestRepository.save(revokedRecord);
        }

        // 3. Clear cached telemetry frame counter or runtime authorization
        if (telemetryIngestionService != null) {
            try {
                telemetryIngestionService.clearNodeCache(cleanNodeId);
            } catch (Exception e) {
                log.warn("Failed to clear telemetry frame counter cache for node {}: {}", cleanNodeId, e.getMessage());
            }
        }

        // 4. Audit Log
        auditLogRepository.save(SystemAuditLog.builder()
                .eventType("NODE_UNPAIRED")
                .actor(effectiveActor)
                .entityType("EdgeNode")
                .entityId(cleanNodeId)
                .previousState(previousState)
                .resultingState("REVOKED")
                .payloadJson(String.format("{\"nodeId\":\"%s\",\"action\":\"FORCEFUL_UNPAIR\",\"revokedBy\":\"%s\"}",
                        cleanNodeId, effectiveActor))
                .createdAt(now)
                .build());

        // 5. Broadcast real-time events to all connected clients
        try {
            systemEventPublisher.publishPairingRequestUpdated(cleanNodeId, "REVOKED");
            systemEventPublisher.publishNodeStatusChanged(cleanNodeId, fieldId, previousState, "UNREGISTERED");
        } catch (Exception e) {
            log.warn("Failed to broadcast unpair event for node {}: {}", cleanNodeId, e.getMessage());
        }

        log.info("Forceful unpairing completed for node {} by {}", cleanNodeId, effectiveActor);

        return NodeUnpairResponse.builder()
                .nodeId(cleanNodeId)
                .unpaired(true)
                .previousStatus(previousState)
                .revokedAt(now)
                .message(String.format("Node %s has been successfully unpaired. Pairing authorization has been revoked.", cleanNodeId))
                .build();
    }

    private PairingRequestResponse toPairingResponse(NodePairingRequest req) {
        Optional<EdgeNode> nodeOpt = edgeNodeRepository.findByNodeId(req.getNodeId());
        boolean recognized = nodeOpt.isPresent();
        Long edgeNodeId = nodeOpt.map(EdgeNode::getId).orElse(null);
        String lifecycle = nodeOpt.map(n -> n.getLifecycleState().name()).orElse(null);
        Long zoneId = (nodeOpt.isPresent() && nodeOpt.get().getMonitoringZone() != null)
                ? nodeOpt.get().getMonitoringZone().getId() : null;
        String zoneName = (nodeOpt.isPresent() && nodeOpt.get().getMonitoringZone() != null)
                ? nodeOpt.get().getMonitoringZone().getName() : null;

        PairingStatus status = req.isExpired() ? PairingStatus.EXPIRED : req.getPairingStatus();

        return PairingRequestResponse.builder()
                .id(req.getId())
                .nodeId(req.getNodeId())
                .hardwareModel(req.getHardwareModel())
                .firmwareVersion(req.getFirmwareVersion())
                .macAddress(req.getMacAddress())
                .pairingStatus(status)
                .rejectionReason(req.getRejectionReason())
                .requestedAt(req.getRequestedAt())
                .expiresAt(req.getExpiresAt())
                .expired(req.isExpired())
                .processedAt(req.getProcessedAt())
                .processedBy(req.getProcessedBy())
                .recognized(recognized)
                .existingEdgeNodeId(edgeNodeId)
                .existingLifecycleState(lifecycle)
                .existingZoneId(zoneId)
                .existingZoneName(zoneName)
                .build();
    }

    // -------------------------------------------------------------
    // Core Health & Overview
    // -------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public CoreHealthResponse getCoreHealth() {
        boolean masterOn = isMasterEnabled();
        boolean dbOk = true;
        long totalNodes = 0;
        int onlineCount = 0;
        int pendingPairing = 0;

        try {
            totalNodes = edgeNodeRepository.count();
            List<EdgeNode> nodes = edgeNodeRepository.findAll();
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(15);
            onlineCount = (int) nodes.stream()
                    .filter(n -> n.getHealthMetrics() != null &&
                            n.getHealthMetrics().getLastHeartbeat() != null &&
                            n.getHealthMetrics().getLastHeartbeat().isAfter(threshold))
                    .count();
            pendingPairing = pairingRequestRepository.findByPairingStatusOrderByRequestedAtDesc(PairingStatus.PENDING).size();
        } catch (Exception e) {
            dbOk = false;
            log.error("Database health check failed: {}", e.getMessage());
        }

        int wsSessions = 0;
        if (realtimeEventPublisherService != null) {
            wsSessions = realtimeEventPublisherService.getActiveSessionCount();
        }

        String status = (!dbOk) ? "DOWN" : (!masterOn ? "DEGRADED" : "UP");

        Map<String, Object> details = new HashMap<>();
        details.put("masterEnabled", masterOn);
        details.put("dbConnectivity", dbOk);
        details.put("activeWebSocketSessions", wsSessions);
        details.put("totalEdgeNodes", totalNodes);

        return CoreHealthResponse.builder()
                .status(status)
                .masterEnabled(masterOn)
                .databaseConnected(dbOk)
                .telemetryIngestionActive(masterOn)
                .activeWebSocketSessions(wsSessions)
                .recognizedNodesCount((int) totalNodes)
                .onlineNodesCount(onlineCount)
                .pendingPairingRequestsCount(pendingPairing)
                .timestamp(LocalDateTime.now())
                .details(details)
                .build();
    }
}

