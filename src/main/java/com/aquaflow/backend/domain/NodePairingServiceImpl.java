package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.request.MasterStateUpdateRequest;
import com.aquaflow.backend.dto.request.NodePairingSubmissionRequest;
import com.aquaflow.backend.dto.request.NodePairingResetRequest;
import com.aquaflow.backend.dto.request.PairingCompletionRequest;
import com.aquaflow.backend.dto.request.PairingRejectionRequest;
import com.aquaflow.backend.dto.request.RegisterNodeRequest;
import com.aquaflow.backend.dto.response.*;
import com.aquaflow.backend.entity.*;
import com.aquaflow.backend.infrastructure.event.SystemEventPublisher;
import com.aquaflow.backend.infrastructure.exception.ResourceNotFoundException;
import com.aquaflow.backend.infrastructure.exception.ValidationException;
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
    private final com.aquaflow.backend.persistence.DeviceCredentialRepository deviceCredentialRepository;

    @Autowired(required = false)
    private MonitoringZoneRepository monitoringZoneRepository;

    @Autowired(required = false)
    private MonitoringPointRepository monitoringPointRepository;

    @Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private TelemetryIngestionService telemetryIngestionService;

    @Autowired(required = false)
    private com.aquaflow.backend.persistence.NodeAssignmentHistoryRepository nodeAssignmentHistoryRepository;

    @Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private FarmlandValidationService farmlandValidationService;

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
        LocalDateTime now = LocalDateTime.now();

        // 1. Mandatory Already-Paired Detection & Loop Prevention
        Optional<EdgeNode> existingNode = edgeNodeRepository.findByNodeId(nodeId);
        boolean isCredentialOperational = false;
        if (deviceCredentialRepository != null) {
            var credOpt = deviceCredentialRepository.findByNodeId(nodeId);
            if (credOpt.isPresent() && credOpt.get().getCredentialState() == DeviceCredentialState.OPERATIONAL) {
                isCredentialOperational = true;
            }
        }
        Optional<NodePairingRequest> latestPairingOpt = pairingRequestRepository
                .findFirstByNodeIdOrderByRequestedAtDesc(nodeId);
        boolean isPairedRecord = latestPairingOpt.isPresent() &&
                (latestPairingOpt.get().getPairingStatus() == PairingStatus.PAIRED ||
                 latestPairingOpt.get().getPairingStatus() == PairingStatus.APPROVED);

        if (existingNode.isPresent() && existingNode.get().getLifecycleState() == NodeLifecycleState.ACTIVE &&
                (isCredentialOperational || isPairedRecord)) {
            EdgeNode node = existingNode.get();
            log.info("Node {} is already registered, commissioned, and paired. Returning ALREADY_PAIRED.", nodeId);
            return PairingRequestResponse.builder()
                    .id(latestPairingOpt.map(NodePairingRequest::getId).orElse(null))
                    .nodeId(nodeId)
                    .hardwareModel(node.getHardwareModel() != null ? node.getHardwareModel() : request.getHardwareModel())
                    .firmwareVersion(node.getFirmwareVersion() != null ? node.getFirmwareVersion() : request.getFirmwareVersion())
                    .macAddress(node.getIdentity() != null ? node.getIdentity().getMacAddress() : request.getMacAddress())
                    .pairingStatus(PairingStatus.ALREADY_PAIRED)
                    .paired(true)
                    .action("RESUME_OPERATION")
                    .recognized(true)
                    .existingEdgeNodeId(node.getId())
                    .existingLifecycleState(node.getLifecycleState() != null ? node.getLifecycleState().name() : "ACTIVE")
                    .existingZoneId(node.getMonitoringZone() != null ? node.getMonitoringZone().getId() : null)
                    .existingZoneName(node.getMonitoringZone() != null ? node.getMonitoringZone().getName() : null)
                    .requestedAt(latestPairingOpt.map(NodePairingRequest::getRequestedAt).orElse(now))
                    .expiresAt(latestPairingOpt.map(NodePairingRequest::getExpiresAt).orElse(now.plusYears(1)))
                    .build();
        }

        // Update hardware/identity metadata on edge node if recognized
        if (existingNode.isPresent()) {
            EdgeNode node = existingNode.get();
            if (request.getMacAddress() != null && node.getIdentity() != null) {
                node.getIdentity().setMacAddress(request.getMacAddress());
                node.getIdentity().setIdentityValue(request.getMacAddress());
            }
            if (request.getHardwareModel() != null) node.setHardwareModel(request.getHardwareModel());
            if (request.getFirmwareVersion() != null) node.setFirmwareVersion(request.getFirmwareVersion());
            if (node.getHealthMetrics() != null) {
                node.getHealthMetrics().setLastHeartbeat(now);
            }
            edgeNodeRepository.save(node);
        }

        // Ensure bootstrap credential exists for node
        if (deviceCredentialRepository != null) {
            deviceCredentialRepository.findByNodeId(nodeId).orElseGet(() -> {
                String bootKey = (request.getDeviceKeyId() != null && !request.getDeviceKeyId().isBlank())
                        ? request.getDeviceKeyId() : "bootstrap-" + nodeId;
                String secret = "dev_sec_" + UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
                return deviceCredentialRepository.save(DeviceCredential.builder()
                        .nodeId(nodeId)
                        .bootstrapKeyId(bootKey)
                        .bootstrapSecret(secret)
                        .credentialState(DeviceCredentialState.BOOTSTRAP)
                        .installationId(request.getInstallationId() != null ? request.getInstallationId() : "default")
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
            });
        }

        // 2. Manage PENDING pairing request with 30-minute expiration window (deduplication)
        Optional<NodePairingRequest> existingPending = pairingRequestRepository
                .findFirstByNodeIdAndPairingStatusOrderByRequestedAtDesc(nodeId, PairingStatus.PENDING);

        NodePairingRequest entity;
        if (existingPending.isPresent()) {
            NodePairingRequest pending = existingPending.get();
            if (pending.isExpired()) {
                pending.setPairingStatus(PairingStatus.EXPIRED);
                pending.setProcessedAt(now);
                pending.setProcessedBy("SYSTEM_EXPIRATION");
                pairingRequestRepository.save(pending);

                // Expired request: create fresh PENDING request with new 30-minute window
                entity = NodePairingRequest.builder()
                        .nodeId(nodeId)
                        .macAddress(request.getMacAddress())
                        .hardwareModel(request.getHardwareModel())
                        .firmwareVersion(request.getFirmwareVersion())
                        .deviceKeyId(request.getDeviceKeyId())
                        .installationId(request.getInstallationId() != null ? request.getInstallationId() : "default")
                        .pairingStatus(PairingStatus.PENDING)
                        .requestedAt(now)
                        .expiresAt(now.plusMinutes(30))
                        .updatedAt(now)
                        .build();
                entity = pairingRequestRepository.save(entity);
                log.info("Previous pairing request expired; created fresh PENDING request #{} for node {}", entity != null ? entity.getId() : "0", nodeId);
            } else {
                entity = pending;
                // Active window: reuse existing request without extending expiration
                if (request.getMacAddress() != null) entity.setMacAddress(request.getMacAddress());
                if (request.getHardwareModel() != null) entity.setHardwareModel(request.getHardwareModel());
                if (request.getFirmwareVersion() != null) entity.setFirmwareVersion(request.getFirmwareVersion());
                if (request.getDeviceKeyId() != null) entity.setDeviceKeyId(request.getDeviceKeyId());
                entity.setUpdatedAt(now);
                entity = pairingRequestRepository.save(entity);
                log.info("Reused existing active PENDING pairing request #{} for node {} without extending expiration (expires at {})",
                        entity != null ? entity.getId() : "0", nodeId, entity != null ? entity.getExpiresAt() : now);
                PairingRequestResponse res = toPairingResponse(entity != null ? entity : pending);
                res.setAction("WAIT_FOR_APPROVAL");
                res.setPaired(false);
                return res;
            }
        } else {
            // Check for existing active APPROVED or PROVISIONING request to resume
            Optional<NodePairingRequest> existingApproved = pairingRequestRepository
                    .findFirstByNodeIdAndPairingStatusOrderByRequestedAtDesc(nodeId, PairingStatus.APPROVED);
            if (existingApproved.isPresent() && !existingApproved.get().isExpired()) {
                NodePairingRequest approved = existingApproved.get();
                log.info("Reusing existing APPROVED pairing request #{} for node {}", approved.getId(), nodeId);
                PairingRequestResponse res = toPairingResponse(approved);
                res.setAction("PROVISION");
                res.setPaired(false);
                return res;
            }

            Optional<NodePairingRequest> existingProvisioning = pairingRequestRepository
                    .findFirstByNodeIdAndPairingStatusOrderByRequestedAtDesc(nodeId, PairingStatus.PROVISIONING);
            if (existingProvisioning.isPresent() && !existingProvisioning.get().isExpired()) {
                NodePairingRequest prov = existingProvisioning.get();
                log.info("Reusing existing PROVISIONING pairing request #{} for node {}", prov.getId(), nodeId);
                PairingRequestResponse res = toPairingResponse(prov);
                res.setAction("PROVISION");
                res.setPaired(false);
                return res;
            }
            NodePairingRequest toSave = NodePairingRequest.builder()
                    .nodeId(nodeId)
                    .macAddress(request.getMacAddress())
                    .hardwareModel(request.getHardwareModel())
                    .firmwareVersion(request.getFirmwareVersion())
                    .deviceKeyId(request.getDeviceKeyId())
                    .installationId(request.getInstallationId() != null ? request.getInstallationId() : "default")
                    .pairingStatus(PairingStatus.PENDING)
                    .requestedAt(now)
                    .expiresAt(now.plusMinutes(30))
                    .updatedAt(now)
                    .build();
            NodePairingRequest saved = pairingRequestRepository.save(toSave);
            entity = (saved != null) ? saved : toSave;
            log.info("Created new PENDING pairing request #{} for node {} (expires at {})", entity.getId(), nodeId, entity.getExpiresAt());
        }

        // Audit log
        auditLogRepository.save(SystemAuditLog.builder()
                .eventType("NODE_PAIRING_REQUESTED")
                .actor(nodeId)
                .entityType("NodePairingRequest")
                .entityId(String.valueOf(entity != null ? entity.getId() : "0"))
                .previousState("NONE")
                .resultingState("PENDING")
                .payloadJson(String.format("{\"nodeId\":\"%s\",\"mac\":\"%s\"}", nodeId, request.getMacAddress()))
                .createdAt(now)
                .build());

        // Notify realtime subscribers
        try {
            systemEventPublisher.publishPairingRequestUpdated(nodeId, "PENDING");
        } catch (Exception e) {
            log.warn("Failed to publish pairing request event: {}", e.getMessage());
        }

        PairingRequestResponse response = toPairingResponse(entity);
        response.setAction("WAIT_FOR_APPROVAL");
        response.setPaired(false);
        return response;
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
                    PairingRequestResponse resp = toPairingResponse(latestReq.get());
                    resp.setPairingStatus(PairingStatus.REVOKED);
                    resp.setAction("REVOKED");
                    resp.setPaired(false);
                    return resp;
                }
                return PairingRequestResponse.builder()
                        .nodeId(cleanNodeId)
                        .hardwareModel(node.getHardwareModel())
                        .firmwareVersion(node.getFirmwareVersion())
                        .macAddress(node.getIdentity() != null ? node.getIdentity().getMacAddress() : null)
                        .pairingStatus(PairingStatus.REVOKED)
                        .action("REVOKED")
                        .paired(false)
                        .recognized(false)
                        .existingEdgeNodeId(node.getId())
                        .existingLifecycleState(node.getLifecycleState() != null ? node.getLifecycleState().name() : null)
                        .build();
            }

            // If active and paired, return ALREADY_PAIRED / PAIRED
            if (node.getLifecycleState() == NodeLifecycleState.ACTIVE) {
                if (latestReq.isPresent()) {
                    PairingRequestResponse resp = toPairingResponse(latestReq.get());
                    resp.setPaired(true);
                    resp.setAction("RESUME_OPERATION");
                    return resp;
                }
                return PairingRequestResponse.builder()
                        .nodeId(cleanNodeId)
                        .hardwareModel(node.getHardwareModel())
                        .firmwareVersion(node.getFirmwareVersion())
                        .macAddress(node.getIdentity() != null ? node.getIdentity().getMacAddress() : null)
                        .pairingStatus(PairingStatus.PAIRED)
                        .paired(true)
                        .action("RESUME_OPERATION")
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
            return PairingRequestResponse.builder()
                    .nodeId(cleanNodeId)
                    .hardwareModel(node.getHardwareModel())
                    .firmwareVersion(node.getFirmwareVersion())
                    .macAddress(node.getIdentity() != null ? node.getIdentity().getMacAddress() : null)
                    .pairingStatus(PairingStatus.APPROVED)
                    .action("PROVISION")
                    .paired(false)
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
            if (request.getMonitoringZoneId() == null && existing.getMonitoringZone() != null) {
                existing.setMonitoringZone(null);
                edgeNodeRepository.save(existing);
            }
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

        // Synchronize assignment history & monitoring points
        if (nodeAssignmentHistoryRepository != null && nodeResponse.getId() != null) {
            try {
                var activeHistOpt = nodeAssignmentHistoryRepository.findActiveAssignment(nodeResponse.getId());
                if (request.getMonitoringZoneId() == null) {
                    activeHistOpt.ifPresent(h -> {
                        h.setUnassignedAt(LocalDateTime.now());
                        h.setReason("Approved as unassigned");
                        nodeAssignmentHistoryRepository.save(h);
                    });
                } else if (monitoringZoneRepository != null) {
                    MonitoringZone zone = monitoringZoneRepository.findById(request.getMonitoringZoneId()).orElse(null);
                    if (zone != null) {
                        if (activeHistOpt.isPresent() && !Objects.equals(activeHistOpt.get().getMonitoringZoneId(), zone.getId())) {
                            NodeAssignmentHistory prev = activeHistOpt.get();
                            prev.setUnassignedAt(LocalDateTime.now());
                            nodeAssignmentHistoryRepository.save(prev);
                            activeHistOpt = Optional.empty();
                        }
                        if (activeHistOpt.isEmpty()) {
                            nodeAssignmentHistoryRepository.save(NodeAssignmentHistory.builder()
                                    .edgeNodeId(nodeResponse.getId())
                                    .nodeId(nodeResponse.getNodeId())
                                    .fieldId(zone.getField() != null ? zone.getField().getId() : null)
                                    .monitoringZoneId(zone.getId())
                                    .assignedAt(LocalDateTime.now())
                                    .assignedBy(actor != null ? actor : "ADMIN")
                                    .reason("Assigned during pairing approval")
                                    .build());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Assignment history sync for node {}: {}", nodeResponse.getNodeId(), e.getMessage());
            }
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
    public PairingProvisionResponse provisionOperationalCredential(Long pairingRequestId, String actor) {
        NodePairingRequest pairingReq = pairingRequestRepository.findById(pairingRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Pairing request not found: " + pairingRequestId));

        if (pairingReq.getPairingStatus() != PairingStatus.APPROVED &&
                pairingReq.getPairingStatus() != PairingStatus.PROVISIONING) {
            throw new ValidationException("PAIRING_NOT_APPROVED: Pairing request " + pairingRequestId +
                    " is in state " + pairingReq.getPairingStatus() + ", expected APPROVED");
        }

        String nodeId = pairingReq.getNodeId();
        LocalDateTime now = LocalDateTime.now();

        DeviceCredential credential = (deviceCredentialRepository != null)
                ? deviceCredentialRepository.findByNodeId(nodeId).orElseGet(() -> DeviceCredential.builder()
                        .nodeId(nodeId)
                        .bootstrapKeyId("bootstrap-" + nodeId)
                        .bootstrapSecret("dev_sec_" + UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""))
                        .credentialState(DeviceCredentialState.BOOTSTRAP)
                        .installationId(pairingReq.getInstallationId() != null ? pairingReq.getInstallationId() : "default")
                        .createdAt(now)
                        .updatedAt(now)
                        .build())
                : DeviceCredential.builder()
                        .nodeId(nodeId)
                        .bootstrapKeyId("bootstrap-" + nodeId)
                        .bootstrapSecret("dev_sec_" + UUID.randomUUID().toString().replace("-", ""))
                        .credentialState(DeviceCredentialState.BOOTSTRAP)
                        .installationId(pairingReq.getInstallationId() != null ? pairingReq.getInstallationId() : "default")
                        .createdAt(now)
                        .updatedAt(now)
                        .build();

        // Idempotent: reuse existing operational secret if already generated for this pairing session, otherwise create fresh
        String operKeyId = credential.getOperationalKeyId();
        String operSecret = credential.getOperationalSecret();
        if (operKeyId == null || operSecret == null || operSecret.isBlank()) {
            operKeyId = "oper-" + nodeId + "-" + System.currentTimeMillis();
            operSecret = "oper_sec_" + UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            credential.setOperationalKeyId(operKeyId);
            credential.setOperationalSecret(operSecret);
            credential.setUpdatedAt(now);
            if (deviceCredentialRepository != null) {
                deviceCredentialRepository.save(credential);
            }
        }

        pairingReq.setPairingStatus(PairingStatus.PROVISIONING);
        pairingReq.setOperationalKeyId(operKeyId);
        pairingReq.setUpdatedAt(now);
        pairingRequestRepository.save(pairingReq);

        log.info("Operational credential provisioned for node {} (keyId={})", nodeId, operKeyId);

        return PairingProvisionResponse.builder()
                .pairingRequestId(pairingReq.getId())
                .nodeId(nodeId)
                .operationalKeyId(operKeyId)
                .operationalSecret(operSecret)
                .status(PairingStatus.PROVISIONING)
                .action("CONFIRM")
                .expiresAt(pairingReq.getExpiresAt())
                .build();
    }

    @Override
    public PairingRequestResponse completePairing(Long pairingRequestId, PairingCompletionRequest request, String actor) {
        NodePairingRequest pairingReq = pairingRequestRepository.findById(pairingRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Pairing request not found: " + pairingRequestId));

        LocalDateTime now = LocalDateTime.now();
        String nodeId = pairingReq.getNodeId();

        if (pairingReq.getPairingStatus() != PairingStatus.PROVISIONING &&
                pairingReq.getPairingStatus() != PairingStatus.APPROVED) {
            if (pairingReq.getPairingStatus() == PairingStatus.PAIRED) {
                // Idempotent replay: already completed
                PairingRequestResponse res = toPairingResponse(pairingReq);
                res.setAction("RESUME_OPERATION");
                res.setPaired(true);
                return res;
            }
            throw new ValidationException("PAIRING_STATE_CONFLICT: Pairing request is in state " +
                    pairingReq.getPairingStatus() + ", expected PROVISIONING");
        }

        if (deviceCredentialRepository != null) {
            deviceCredentialRepository.findByNodeId(nodeId).ifPresent(cred -> {
                cred.setCredentialState(DeviceCredentialState.OPERATIONAL);
                cred.setUpdatedAt(now);
                deviceCredentialRepository.save(cred);
            });
        }

        pairingReq.setPairingStatus(PairingStatus.PAIRED);
        pairingReq.setProcessedAt(now);
        pairingReq.setProcessedBy(actor != null ? actor : "ESP32_COMPLETION");
        pairingReq.setUpdatedAt(now);
        NodePairingRequest saved = pairingRequestRepository.save(pairingReq);

        // Ensure EdgeNode has ACTIVE lifecycleState
        Optional<EdgeNode> nodeOpt = edgeNodeRepository.findByNodeId(nodeId);
        if (nodeOpt.isPresent()) {
            EdgeNode node = nodeOpt.get();
            node.setLifecycleState(NodeLifecycleState.ACTIVE);
            edgeNodeRepository.save(node);
        }

        // Audit log
        auditLogRepository.save(SystemAuditLog.builder()
                .eventType("NODE_PAIRING_COMPLETED")
                .actor(actor != null ? actor : nodeId)
                .entityType("NodePairingRequest")
                .entityId(String.valueOf(pairingRequestId))
                .previousState("PROVISIONING")
                .resultingState("PAIRED")
                .payloadJson(String.format("{\"nodeId\":\"%s\",\"keyId\":\"%s\"}", nodeId, pairingReq.getOperationalKeyId()))
                .createdAt(now)
                .build());

        try {
            systemEventPublisher.publishPairingRequestUpdated(nodeId, "PAIRED");
        } catch (Exception e) {
            log.warn("Failed to publish PAIRED event: {}", e.getMessage());
        }

        PairingRequestResponse response = toPairingResponse(saved);
        response.setAction("RESUME_OPERATION");
        response.setPaired(true);
        return response;
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

        // Revoke device credentials
        if (deviceCredentialRepository != null) {
            deviceCredentialRepository.findByNodeId(cleanNodeId).ifPresent(cred -> {
                cred.setCredentialState(DeviceCredentialState.REVOKED);
                cred.setOperationalSecret(null);
                cred.setOperationalKeyId(null);
                cred.setUpdatedAt(now);
                deviceCredentialRepository.save(cred);
                log.info("Revoked operational credential for unpaired node {}", cleanNodeId);
            });
        }

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

            // Close active assignment history record
            if (nodeAssignmentHistoryRepository != null) {
                try {
                    var activeHistOpt = nodeAssignmentHistoryRepository.findActiveAssignment(node.getId());
                    activeHistOpt.ifPresent(h -> {
                        h.setUnassignedAt(now);
                        h.setReason("Node forcefully unpaired");
                        nodeAssignmentHistoryRepository.save(h);
                    });
                } catch (Exception e) {
                    log.warn("Error closing assignment history for node {}: {}", cleanNodeId, e.getMessage());
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

        // 4. Update farmland operational status if field association was cleared
        if (farmlandValidationService != null && fieldId != null) {
            try {
                farmlandValidationService.updateFarmlandOperationalStatus(fieldId);
            } catch (Exception ex) {
                log.warn("Could not update operational status for field {}: {}", fieldId, ex.getMessage());
            }
        }

        // 5. Audit Log
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

    @Override
    public NodePairingResetResponse resetNodePairing(String nodeId, String reason, String actor) {
        NodePairingResetRequest request = NodePairingResetRequest.builder()
                .nodeIds(List.of(nodeId))
                .reason(reason)
                .clearFieldAssignments(true)
                .build();
        return resetPairing(request, actor);
    }

    @Override
    public NodePairingResetResponse resetPairing(NodePairingResetRequest request, String actor) {
        String effectiveActor = (actor != null && !actor.isBlank()) ? actor : "ADMIN";
        String reason = (request != null && request.getReason() != null && !request.getReason().isBlank())
                ? request.getReason() : "Pairing state reset by administrator";
        boolean clearFieldAssignments = request == null || request.isClearFieldAssignments();

        List<String> targetNodeIds;
        if (request != null && request.getNodeIds() != null && !request.getNodeIds().isEmpty()) {
            targetNodeIds = request.getNodeIds().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .toList();
        } else {
            targetNodeIds = edgeNodeRepository.findAll().stream()
                    .map(EdgeNode::getNodeId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            // Revoke all pending pairing requests across the system
            try {
                List<NodePairingRequest> pendingRequests = pairingRequestRepository.findByPairingStatusOrderByRequestedAtDesc(PairingStatus.PENDING);
                for (NodePairingRequest req : pendingRequests) {
                    req.setPairingStatus(PairingStatus.REVOKED);
                    req.setRejectionReason(reason);
                    req.setProcessedAt(LocalDateTime.now());
                    req.setProcessedBy(effectiveActor);
                    pairingRequestRepository.save(req);
                    systemEventPublisher.publishPairingRequestUpdated(req.getNodeId(), "REVOKED");
                }
            } catch (Exception e) {
                log.warn("Failed to revoke pending pairing requests during reset: {}", e.getMessage());
            }
        }

        List<String> succeeded = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (String nodeId : targetNodeIds) {
            try {
                boolean done = doResetSingleNodePairing(nodeId, reason, clearFieldAssignments, effectiveActor);
                if (done) {
                    succeeded.add(nodeId);
                } else {
                    failed.add(nodeId);
                }
            } catch (Exception e) {
                log.error("Failed to reset pairing for node {}: {}", nodeId, e.getMessage(), e);
                failed.add(nodeId);
            }
        }

        String message = String.format("Pairing reset completed: %d succeeded, %d failed out of %d requested.",
                succeeded.size(), failed.size(), targetNodeIds.size());

        return NodePairingResetResponse.builder()
                .totalRequested(targetNodeIds.size())
                .succeededCount(succeeded.size())
                .failedCount(failed.size())
                .resetNodeIds(succeeded)
                .failedNodeIds(failed)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private boolean doResetSingleNodePairing(String nodeId, String reason, boolean clearFieldAssignments, String effectiveActor) {
        String cleanNodeId = (nodeId != null) ? nodeId.trim() : "";
        if (cleanNodeId.isBlank()) return false;

        LocalDateTime now = LocalDateTime.now();
        String previousState = "UNREGISTERED";
        Long fieldId = null;

        // 1. Reconcile EdgeNode if present in registry
        Optional<EdgeNode> nodeOpt = edgeNodeRepository.findByNodeId(cleanNodeId);
        if (nodeOpt.isPresent()) {
            EdgeNode node = nodeOpt.get();
            previousState = node.getLifecycleState() != null ? node.getLifecycleState().name() : "ACTIVE";

            if (node.getMonitoringZone() != null && node.getMonitoringZone().getField() != null) {
                fieldId = node.getMonitoringZone().getField().getId();
            }

            if (clearFieldAssignments) {
                // Disassociate from monitoring points
                if (monitoringPointRepository != null) {
                    try {
                        List<MonitoringPoint> points = monitoringPointRepository.findByEdgeNodeId(node.getId());
                        if (points != null && !points.isEmpty()) {
                            points.forEach(p -> p.setEdgeNode(null));
                            monitoringPointRepository.saveAll(points);
                        }
                    } catch (Exception e) {
                        log.warn("Error disassociating monitoring points during reset for node {}: {}", cleanNodeId, e.getMessage());
                    }
                }

                // Close active assignment history record
                if (nodeAssignmentHistoryRepository != null) {
                    try {
                        var activeHistOpt = nodeAssignmentHistoryRepository.findActiveAssignment(node.getId());
                        activeHistOpt.ifPresent(h -> {
                            h.setUnassignedAt(now);
                            h.setReason("Pairing reset: " + reason);
                            nodeAssignmentHistoryRepository.save(h);
                        });
                    } catch (Exception e) {
                        log.warn("Error closing assignment history for node {}: {}", cleanNodeId, e.getMessage());
                    }
                }

                node.setMonitoringZone(null);
            }

            node.setLifecycleState(NodeLifecycleState.UNREGISTERED);
            edgeNodeRepository.save(node);
            log.info("Reset EdgeNode {} lifecycleState to UNREGISTERED", cleanNodeId);
        }

        // 2. Revoke and expire all pairing requests for this node
        List<NodePairingRequest> nodeRequests = pairingRequestRepository.findAll()
                .stream()
                .filter(r -> cleanNodeId.equalsIgnoreCase(r.getNodeId()))
                .toList();

        for (NodePairingRequest req : nodeRequests) {
            req.setPairingStatus(PairingStatus.REVOKED);
            req.setRejectionReason(reason);
            req.setProcessedAt(now);
            req.setProcessedBy(effectiveActor);
            pairingRequestRepository.save(req);
        }

        if (nodeRequests.isEmpty()) {
            NodePairingRequest revokedRecord = NodePairingRequest.builder()
                    .nodeId(cleanNodeId)
                    .pairingStatus(PairingStatus.REVOKED)
                    .rejectionReason(reason)
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
                log.warn("Failed to clear telemetry cache for reset node {}: {}", cleanNodeId, e.getMessage());
            }
        }

        // 4. Update farmland operational status if field association was cleared
        if (farmlandValidationService != null && fieldId != null) {
            try {
                farmlandValidationService.updateFarmlandOperationalStatus(fieldId);
            } catch (Exception ex) {
                log.warn("Could not update operational status for field {}: {}", fieldId, ex.getMessage());
            }
        }

        // 5. Audit Log
        auditLogRepository.save(SystemAuditLog.builder()
                .eventType("NODE_PAIRING_RESET")
                .actor(effectiveActor)
                .entityType("EdgeNode")
                .entityId(cleanNodeId)
                .previousState(previousState)
                .resultingState("RESET_REVOKED")
                .payloadJson(String.format("{\"nodeId\":\"%s\",\"action\":\"PAIRING_RESET\",\"reason\":\"%s\",\"by\":\"%s\"}",
                        cleanNodeId, reason, effectiveActor))
                .createdAt(now)
                .build());

        // Revoke device credentials on reset
        if (deviceCredentialRepository != null) {
            deviceCredentialRepository.findByNodeId(cleanNodeId).ifPresent(cred -> {
                cred.setCredentialState(DeviceCredentialState.REVOKED);
                cred.setOperationalSecret(null);
                cred.setOperationalKeyId(null);
                cred.setUpdatedAt(now);
                deviceCredentialRepository.save(cred);
                log.info("Revoked operational credential for reset node {}", cleanNodeId);
            });
        }

        // 6. Broadcast real-time events to all connected clients
        try {
            systemEventPublisher.publishPairingRequestUpdated(cleanNodeId, "REVOKED");
            systemEventPublisher.publishNodeStatusChanged(cleanNodeId, fieldId, previousState, "UNREGISTERED");
        } catch (Exception e) {
            log.warn("Failed to broadcast reset event for node {}: {}", cleanNodeId, e.getMessage());
        }

        return true;
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

        // Completed pairing relationships for ACTIVE nodes never expire automatically
        PairingStatus status = (req.isExpired() && req.getPairingStatus() != PairingStatus.PAIRED && req.getPairingStatus() != PairingStatus.ALREADY_PAIRED)
                ? PairingStatus.EXPIRED : req.getPairingStatus();
        boolean isPaired = status == PairingStatus.PAIRED || status == PairingStatus.ALREADY_PAIRED ||
                (nodeOpt.isPresent() && nodeOpt.get().getLifecycleState() == NodeLifecycleState.ACTIVE);

        String action;
        if (isPaired) {
            action = "RESUME_OPERATION";
        } else if (status == PairingStatus.APPROVED || status == PairingStatus.PROVISIONING) {
            action = "PROVISION";
        } else if (status == PairingStatus.PENDING) {
            action = "WAIT_FOR_APPROVAL";
        } else if (status == PairingStatus.REJECTED) {
            action = "REJECTED";
        } else if (status == PairingStatus.EXPIRED) {
            action = "EXPIRED";
        } else {
            action = "REVOKED";
        }

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
                .action(action)
                .paired(isPaired)
                .deviceKeyId(req.getDeviceKeyId())
                .installationId(req.getInstallationId())
                .operationalKeyId(req.getOperationalKeyId())
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

        String status = (!dbOk) ? "DOWN" : (!masterOn ? "DEGRADED" : "UP");

        Map<String, Object> details = new HashMap<>();
        details.put("masterEnabled", masterOn);
        details.put("dbConnectivity", dbOk);
        details.put("syncMode", "REST_DATABASE_DRIVEN");
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

