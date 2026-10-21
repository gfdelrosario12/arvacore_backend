package com.aquaflow.backend.domain;

import com.aquaflow.backend.entity.CommandState;
import com.aquaflow.backend.entity.CommandStateTransitionHistory;
import com.aquaflow.backend.entity.IrrigationCommandRecord;
import com.aquaflow.backend.infrastructure.exception.ResourceNotFoundException;
import com.aquaflow.backend.infrastructure.exception.UnauthorizedException;
import com.aquaflow.backend.infrastructure.exception.ValidationException;
import com.aquaflow.backend.persistence.CommandStateTransitionHistoryRepository;
import com.aquaflow.backend.persistence.IrrigationCommandRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class IrrigationCommandStateMachineImpl implements IrrigationCommandStateMachine {

    private static final Logger log = LoggerFactory.getLogger(IrrigationCommandStateMachineImpl.class);

    private final IrrigationCommandRecordRepository commandRecordRepository;
    private final CommandStateTransitionHistoryRepository historyRepository;
    private final AuditLogService auditLogService;
    private AlertService alertService;

    private static final Map<CommandState, Set<CommandState>> ALLOWED_TRANSITIONS = new EnumMap<>(CommandState.class);

    static {
        ALLOWED_TRANSITIONS.put(CommandState.ACCEPTED, EnumSet.of(CommandState.QUEUED, CommandState.CANCELLED, CommandState.FAILED));
        ALLOWED_TRANSITIONS.put(CommandState.QUEUED, EnumSet.of(CommandState.DOWNLINK_TRANSMITTED, CommandState.CANCELLED, CommandState.FAILED));
        ALLOWED_TRANSITIONS.put(CommandState.DOWNLINK_TRANSMITTED, EnumSet.of(CommandState.EDGE_ACKNOWLEDGED, CommandState.FAILED, CommandState.CANCELLED, CommandState.OVERRIDDEN));
        ALLOWED_TRANSITIONS.put(CommandState.EDGE_ACKNOWLEDGED, EnumSet.of(CommandState.EXECUTING, CommandState.FAILED, CommandState.CANCELLED, CommandState.OVERRIDDEN));
        ALLOWED_TRANSITIONS.put(CommandState.EXECUTING, EnumSet.of(CommandState.COMPLETED, CommandState.FAILED, CommandState.CANCELLED, CommandState.OVERRIDDEN));
        ALLOWED_TRANSITIONS.put(CommandState.COMPLETED, Collections.emptySet());
        ALLOWED_TRANSITIONS.put(CommandState.FAILED, Collections.emptySet());
        ALLOWED_TRANSITIONS.put(CommandState.CANCELLED, Collections.emptySet());
        ALLOWED_TRANSITIONS.put(CommandState.OVERRIDDEN, Collections.emptySet());
    }

    public IrrigationCommandStateMachineImpl(IrrigationCommandRecordRepository commandRecordRepository,
                                             CommandStateTransitionHistoryRepository historyRepository,
                                             @Autowired(required = false) AuditLogService auditLogService) {
        this.commandRecordRepository = commandRecordRepository;
        this.historyRepository = historyRepository;
        this.auditLogService = auditLogService;
    }

    @Autowired(required = false)
    public void setAlertService(AlertService alertService) {
        this.alertService = alertService;
    }

    @Override
    @Transactional
    public IrrigationCommandRecord registerCommand(String correlationId, String commandType, Long targetFieldId,
                                                   Long nodeId, String actorSource, String rationale) {
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        Optional<IrrigationCommandRecord> existing = commandRecordRepository.findByCorrelationId(correlationId);
        if (existing.isPresent()) {
            return existing.get();
        }

        IrrigationCommandRecord record = new IrrigationCommandRecord(
                correlationId,
                commandType != null ? commandType : "COMMAND",
                targetFieldId,
                nodeId,
                CommandState.ACCEPTED,
                actorSource != null ? actorSource : "SYSTEM",
                rationale
        );

        record = commandRecordRepository.save(record);

        CommandStateTransitionHistory history = new CommandStateTransitionHistory(
                correlationId,
                null,
                CommandState.ACCEPTED,
                actorSource,
                "Command registered: " + commandType,
                null
        );
        historyRepository.save(history);

        if (auditLogService != null) {
            auditLogService.logIrrigationEvent("COMMAND_REGISTERED", actorSource, "IrrigationCommand",
                    correlationId, correlationId, Map.of("state", CommandState.ACCEPTED.name(), "type", commandType));
        }

        log.info("Registered command correlationId={} type={} state=ACCEPTED", correlationId, commandType);
        return record;
    }

    @Override
    @Transactional
    public IrrigationCommandRecord transitionState(String correlationId, CommandState targetState, String actorSource,
                                                   String reason, String metadataJson, String operatorId, String authorizationRole) {
        IrrigationCommandRecord record = commandRecordRepository.findByCorrelationId(correlationId)
                .orElseThrow(() -> new ResourceNotFoundException("Command not found with correlationId: " + correlationId));

        CommandState currentState = record.getCurrentState();

        if (currentState == targetState) {
            log.info("Command correlationId={} is already in targetState={}. Idempotent transition.", correlationId, targetState);
            return record;
        }

        // Emergency stop resolution check
        if ("EMERGENCY_STOP".equalsIgnoreCase(record.getCommandType()) || currentState == CommandState.OVERRIDDEN) {
            validateEmergencyStopAuthorization(operatorId, authorizationRole);
        }

        // Validate transition
        Set<CommandState> allowedTargets = ALLOWED_TRANSITIONS.getOrDefault(currentState, Collections.emptySet());
        if (!allowedTargets.contains(targetState)) {
            throw new ValidationException(String.format("Invalid command state transition from %s to %s for correlationId: %s",
                    currentState, targetState, correlationId));
        }

        record.setCurrentState(targetState);
        if (targetState == CommandState.FAILED && reason != null) {
            record.setFailureReason(reason);
        }
        if (metadataJson != null && metadataJson.contains("rssi")) {
            record.setEdgeAckMetrics(metadataJson);
        }

        record = commandRecordRepository.save(record);

        CommandStateTransitionHistory history = new CommandStateTransitionHistory(
                correlationId,
                currentState,
                targetState,
                actorSource != null ? actorSource : "SYSTEM",
                reason,
                metadataJson
        );
        historyRepository.save(history);

        if (auditLogService != null) {
            auditLogService.logSystemEvent("COMMAND_STATE_TRANSITION", actorSource, "IrrigationCommand",
                    correlationId, correlationId, currentState.name(), targetState.name(),
                    Map.of("reason", reason != null ? reason : "", "metadata", metadataJson != null ? metadataJson : ""));
        }

        if (alertService != null && targetState == CommandState.FAILED) {
            alertService.createNotification(
                    "IRRIGATION_COMMAND_FAILED",
                    "Irrigation Command Failed",
                    "Irrigation command (" + record.getCommandType() + ") failed: " + (reason != null ? reason : "Unknown failure"),
                    "CRITICAL",
                    "Irrigation",
                    record.getNodeId() != null ? String.valueOf(record.getNodeId()) : null,
                    record.getTargetFieldId(),
                    null,
                    null,
                    actorSource != null ? actorSource : "SYSTEM",
                    "Irrigation Command Engine",
                    "FIELD",
                    "Check actuator connectivity and retry the irrigation command if needed.",
                    "CMD_FAIL_" + correlationId,
                    60,
                    metadataJson
            );
        }

        log.info("Transitioned command correlationId={} from {} to {}", correlationId, currentState, targetState);
        return record;
    }

    private void validateEmergencyStopAuthorization(String operatorId, String authorizationRole) {
        if (authorizationRole == null || authorizationRole.isBlank()) {
            throw new UnauthorizedException("Explicit operator authorization (ROLE_ADMIN or ROLE_SUPERVISOR) is required to resolve emergency stop commands");
        }
        String upperRole = authorizationRole.toUpperCase();
        if (!upperRole.contains("ADMIN") && !upperRole.contains("SUPERVISOR") && !upperRole.contains("OPERATOR")) {
            throw new UnauthorizedException("Insufficient privileges to clear emergency stop command");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public IrrigationCommandRecord getCommand(String correlationId) {
        return commandRecordRepository.findByCorrelationId(correlationId)
                .orElseThrow(() -> new ResourceNotFoundException("Command not found with correlationId: " + correlationId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommandStateTransitionHistory> getTransitionHistory(String correlationId) {
        return historyRepository.findByCorrelationIdOrderByTimestampAsc(correlationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IrrigationCommandRecord> getFieldCommands(Long fieldId) {
        return commandRecordRepository.findByTargetFieldIdOrderByCreatedAtDesc(fieldId);
    }
}
