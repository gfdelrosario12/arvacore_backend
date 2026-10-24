package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.request.EmergencyStopRequest;
import com.aquaflow.backend.dto.response.CommandStatusResponse;
import com.aquaflow.backend.entity.*;
import com.aquaflow.backend.infrastructure.event.SystemEventPublisher;
import com.aquaflow.backend.infrastructure.exception.ResourceNotFoundException;
import com.aquaflow.backend.infrastructure.exception.ValidationException;
import com.aquaflow.backend.persistence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class EmergencyStopServiceImpl implements EmergencyStopService {

    private static final Logger log = LoggerFactory.getLogger(EmergencyStopServiceImpl.class);

    private final FieldRepository fieldRepository;
    private final EdgeNodeRepository edgeNodeRepository;
    private final MonitoringZoneRepository monitoringZoneRepository;
    private final DownlinkQueueService downlinkQueueService;
    private final IrrigationAuditLogRepository auditLogRepository;
    private final SystemEventPublisher systemEventPublisher;
    private final ObjectMapper objectMapper;
    private final IrrigationCommandStateMachine stateMachine;
    private final java.util.concurrent.atomic.AtomicBoolean emergencyStopActive = new java.util.concurrent.atomic.AtomicBoolean(false);

    public EmergencyStopServiceImpl(FieldRepository fieldRepository,
                                    EdgeNodeRepository edgeNodeRepository,
                                    MonitoringZoneRepository monitoringZoneRepository,
                                    DownlinkQueueService downlinkQueueService,
                                    IrrigationAuditLogRepository auditLogRepository,
                                    SystemEventPublisher systemEventPublisher,
                                    ObjectMapper objectMapper) {
        this(fieldRepository, edgeNodeRepository, monitoringZoneRepository, downlinkQueueService, auditLogRepository, systemEventPublisher, objectMapper, null);
    }

    @Autowired
    public EmergencyStopServiceImpl(FieldRepository fieldRepository,
                                    EdgeNodeRepository edgeNodeRepository,
                                    MonitoringZoneRepository monitoringZoneRepository,
                                    DownlinkQueueService downlinkQueueService,
                                    IrrigationAuditLogRepository auditLogRepository,
                                    @Autowired(required = false) SystemEventPublisher systemEventPublisher,
                                    ObjectMapper objectMapper,
                                    @Autowired(required = false) IrrigationCommandStateMachine stateMachine) {
        this.fieldRepository = fieldRepository;
        this.edgeNodeRepository = edgeNodeRepository;
        this.monitoringZoneRepository = monitoringZoneRepository;
        this.downlinkQueueService = downlinkQueueService;
        this.auditLogRepository = auditLogRepository;
        this.systemEventPublisher = systemEventPublisher;
        this.objectMapper = objectMapper;
        this.stateMachine = stateMachine;
    }

    @Override
    public boolean isEmergencyStopActive() {
        return emergencyStopActive.get();
    }

    @Override
    public void setEmergencyStopActive(boolean active) {
        emergencyStopActive.set(active);
    }

    @Override
    public CommandStatusResponse executeEmergencyStop(EmergencyStopRequest request) {
        emergencyStopActive.set(true);
        log.warn("EXECUTE EMERGENCY STOP requested by operator: {} for fieldId: {}, reason: {}",
                request.getOperatorId(), request.getFieldId(), request.getReason());

        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new ValidationException("Emergency stop requires a valid non-empty reason");
        }

        Long targetFieldId = request.getFieldId();
        if (targetFieldId != null) {
            fieldRepository.findById(targetFieldId)
                    .orElseThrow(() -> new ResourceNotFoundException("Field not found with id: " + targetFieldId));
        }

        String correlationId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        if (stateMachine != null) {
            stateMachine.registerCommand(correlationId, "EMERGENCY_STOP", targetFieldId, null, request.getOperatorId(), request.getReason());
            stateMachine.transitionState(correlationId, CommandState.QUEUED, request.getOperatorId(), request.getReason(), null, request.getOperatorId(), "ROLE_ADMIN");
        }

        // 1. Log audit entry for emergency stop
        saveAuditLog("EMERGENCY_STOP", request.getOperatorId(),
                targetFieldId != null ? "FIELD" : "SYSTEM",
                targetFieldId != null ? targetFieldId.toString() : "ALL",
                request);

        // 2. Build emergency payload JSON
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(Map.of(
                    "command", "EMERGENCY_STOP",
                    "operatorId", request.getOperatorId(),
                    "fieldId", targetFieldId != null ? targetFieldId : 0L,
                    "reason", request.getReason(),
                    "priority", "HIGHEST",
                    "correlationId", correlationId
            ));
        } catch (Exception e) {
            payloadJson = String.format("{\"command\":\"EMERGENCY_STOP\",\"reason\":\"%s\",\"correlationId\":\"%s\"}",
                    request.getReason(), correlationId);
        }

        // 3. Find targeted nodes
        List<EdgeNode> targetNodes = new ArrayList<>();
        if (targetFieldId != null) {
            List<MonitoringZone> zones = monitoringZoneRepository.findByFieldId(targetFieldId);
            for (MonitoringZone zone : zones) {
                targetNodes.addAll(edgeNodeRepository.findByMonitoringZoneId(zone.getId()));
            }
        } else {
            targetNodes.addAll(edgeNodeRepository.findAll());
        }

        // 4. Queue high-priority emergency stop downlinks
        for (EdgeNode node : targetNodes) {
            downlinkQueueService.queueDownlink(node, payloadJson, correlationId);
        }

        // 5. Emit emergency system events and alarms
        if (systemEventPublisher != null) {
            try {
                systemEventPublisher.publishEmergencyStop(
                        correlationId,
                        targetFieldId,
                        request.getReason()
                );
                systemEventPublisher.publishAlarm(
                        correlationId,
                        targetFieldId,
                        "EMERGENCY_STOP",
                        "CRITICAL",
                        "Emergency stop issued by operator " + request.getOperatorId() + ": " + request.getReason()
                );
            } catch (Exception e) {
                log.error("Failed to publish emergency stop event: {}", e.getMessage());
            }
        }

        return CommandStatusResponse.builder()
                .correlationId(correlationId)
                .fieldId(targetFieldId)
                .operatorId(request.getOperatorId())
                .status(CommandLifecycleState.QUEUED)
                .commandType("EMERGENCY_STOP")
                .rationale(request.getReason())
                .details("Emergency stop queued for " + (targetFieldId != null ? "field " + targetFieldId : "all nodes"))
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private void saveAuditLog(String eventType, String actor, String entityType, String entityId, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            IrrigationAuditLog logEntry = IrrigationAuditLog.builder()
                    .eventType(eventType)
                    .actor(actor)
                    .entityType(entityType)
                    .entityId(entityId)
                    .payloadJson(json)
                    .build();
            auditLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to save audit log for emergency stop: {}", e.getMessage());
        }
    }
}
