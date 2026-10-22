package com.aquaflow.backend.api;

import com.aquaflow.backend.domain.AlertService;
import com.aquaflow.backend.domain.DownlinkQueueService;
import com.aquaflow.backend.domain.EmergencyStopService;
import com.aquaflow.backend.domain.IrrigationCommandStateMachine;
import com.aquaflow.backend.domain.IrrigationDecisionService;
import com.aquaflow.backend.domain.ManualControlService;
import com.aquaflow.backend.dto.request.EmergencyStopRequest;
import com.aquaflow.backend.dto.request.IrrigationCommandAckRequest;
import com.aquaflow.backend.dto.request.ManualStartRequest;
import com.aquaflow.backend.dto.request.ManualStopRequest;
import com.aquaflow.backend.dto.response.CommandStatusResponse;
import com.aquaflow.backend.dto.response.FieldIrrigationStatusResponse;
import com.aquaflow.backend.dto.response.PendingCommandResponse;
import com.aquaflow.backend.entity.CommandLifecycleState;
import com.aquaflow.backend.entity.CommandState;
import com.aquaflow.backend.entity.ConfigSyncStatus;
import com.aquaflow.backend.entity.DownlinkQueueItem;
import com.aquaflow.backend.infrastructure.event.SystemEvent;
import com.aquaflow.backend.infrastructure.event.SystemEventPublisher;
import com.aquaflow.backend.infrastructure.event.SystemEventType;
import com.aquaflow.backend.persistence.DownlinkQueueItemRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/irrigation")
public class IrrigationController {

    private static final Logger log = LoggerFactory.getLogger(IrrigationController.class);

    private final ManualControlService manualControlService;
    private final EmergencyStopService emergencyStopService;
    private final IrrigationDecisionService decisionService;
    private final DownlinkQueueItemRepository downlinkQueueItemRepository;
    private final DownlinkQueueService downlinkQueueService;
    private final AlertService alertService;
    private final ObjectMapper objectMapper;
    private final IrrigationCommandStateMachine stateMachine;
    private final SystemEventPublisher systemEventPublisher;

    public IrrigationController(ManualControlService manualControlService,
                                EmergencyStopService emergencyStopService,
                                IrrigationDecisionService decisionService,
                                @Autowired(required = false) DownlinkQueueItemRepository downlinkQueueItemRepository,
                                @Autowired(required = false) DownlinkQueueService downlinkQueueService,
                                @Autowired(required = false) AlertService alertService,
                                @Autowired(required = false) ObjectMapper objectMapper,
                                @Autowired(required = false) IrrigationCommandStateMachine stateMachine,
                                @Autowired(required = false) SystemEventPublisher systemEventPublisher) {
        this.manualControlService = manualControlService;
        this.emergencyStopService = emergencyStopService;
        this.decisionService = decisionService;
        this.downlinkQueueItemRepository = downlinkQueueItemRepository;
        this.downlinkQueueService = downlinkQueueService;
        this.alertService = alertService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.stateMachine = stateMachine;
        this.systemEventPublisher = systemEventPublisher;
    }

    @PostMapping("/manual/start")
    public ResponseEntity<CommandStatusResponse> startManualIrrigation(@Valid @RequestBody ManualStartRequest request) {
        CommandStatusResponse response = manualControlService.startManualIrrigation(request);
        return ResponseEntity.accepted().body(response);
    }

    @PostMapping("/manual/stop")
    public ResponseEntity<CommandStatusResponse> stopManualIrrigation(@Valid @RequestBody ManualStopRequest request) {
        CommandStatusResponse response = manualControlService.stopManualIrrigation(request);
        return ResponseEntity.accepted().body(response);
    }

    @PostMapping("/emergency-stop")
    public ResponseEntity<CommandStatusResponse> executeEmergencyStop(@Valid @RequestBody EmergencyStopRequest request) {
        CommandStatusResponse response = emergencyStopService.executeEmergencyStop(request);
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/command/{correlationId}")
    public ResponseEntity<CommandStatusResponse> getCommandStatus(@PathVariable String correlationId) {
        CommandStatusResponse response = manualControlService.getCommandStatus(correlationId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<FieldIrrigationStatusResponse> getSystemStatus(@RequestParam(defaultValue = "1") Long fieldId) {
        FieldIrrigationStatusResponse response = decisionService.getFieldIrrigationStatus(fieldId);
        return ResponseEntity.ok(response);
    }

    /**
     * Polling endpoint for ArvaCore Pump/Actuator Controller.
     * Returns the oldest PENDING queued command or empty.
     */
    @GetMapping("/pending-command/{controllerId}")
    public ResponseEntity<PendingCommandResponse> getPendingCommand(@PathVariable String controllerId) {
        if (downlinkQueueItemRepository == null) {
            return ResponseEntity.ok(PendingCommandResponse.builder().build());
        }

        List<DownlinkQueueItem> queued = downlinkQueueItemRepository.findByStatus(ConfigSyncStatus.QUEUED);
        for (DownlinkQueueItem item : queued) {
            String payloadJson = item.getPayloadJson();
            String command = null;
            String reason = null;
            Integer durationMinutes = null;
            Long fieldId = 1L;

            if (payloadJson != null && !payloadJson.isBlank()) {
                try {
                    JsonNode node = objectMapper.readTree(payloadJson);
                    if (node.has("command")) command = node.get("command").asText();
                    if (node.has("reason")) reason = node.get("reason").asText();
                    if (node.has("durationMinutes")) durationMinutes = node.get("durationMinutes").asInt();
                    if (node.has("fieldId")) fieldId = node.get("fieldId").asLong();
                } catch (Exception e) {
                    if (payloadJson.contains("EMERGENCY_STOP")) command = "EMERGENCY_STOP";
                    else if (payloadJson.contains("MANUAL_START") || payloadJson.contains("START_IRRIGATION")) command = "START_IRRIGATION";
                    else if (payloadJson.contains("MANUAL_STOP") || payloadJson.contains("STOP_IRRIGATION")) command = "STOP_IRRIGATION";
                    else if (payloadJson.contains("SAFE_STOP")) command = "SAFE_STOP";
                }
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiry = item.getCreatedAt() != null ? item.getCreatedAt().plusMinutes(15) : now.plusMinutes(15);
            if (now.isAfter(expiry)) {
                // Command expired, mark FAILED
                item.setStatus(ConfigSyncStatus.TIMEOUT);
                downlinkQueueItemRepository.save(item);
                continue;
            }

            return ResponseEntity.ok(PendingCommandResponse.builder()
                    .correlationId(item.getCorrelationId())
                    .command(command)
                    .targetControllerId(controllerId)
                    .targetFieldId(fieldId)
                    .reason(reason != null ? reason : "Automated/Manual command")
                    .durationMinutes(durationMinutes != null ? durationMinutes : 15)
                    .timestamp(item.getCreatedAt() != null ? item.getCreatedAt() : now)
                    .expiry(expiry)
                    .payloadJson(payloadJson)
                    .build());
        }

        return ResponseEntity.ok(PendingCommandResponse.builder().build());
    }

    /**
     * Acknowledgment endpoint called by ArvaCore upon receiving or executing commands.
     */
    @PostMapping("/ack/{correlationId}")
    public ResponseEntity<Map<String, Object>> acknowledgeCommand(
            @PathVariable String correlationId,
            @Valid @RequestBody IrrigationCommandAckRequest ack) {
        log.info("Received ACK from controller {} for correlationId: {}, status: {}",
                ack.getControllerId(), correlationId, ack.getStatus());

        if (downlinkQueueService != null) {
            if ("EXECUTED".equalsIgnoreCase(ack.getStatus()) || "RECEIVED".equalsIgnoreCase(ack.getStatus())) {
                downlinkQueueService.handleAcknowledgement(correlationId);
            }
        }

        CommandLifecycleState lifecycleState = switch (ack.getStatus().toUpperCase()) {
            case "EXECUTED" -> CommandLifecycleState.EXECUTED;
            case "RECEIVED" -> CommandLifecycleState.ACKNOWLEDGED;
            case "REJECTED" -> CommandLifecycleState.REJECTED_SAFETY_INTERLOCK;
            default -> CommandLifecycleState.FAILED;
        };

        manualControlService.updateCommandStatus(correlationId, lifecycleState,
                "Controller " + ack.getControllerId() + " reported: " + ack.getStatus() +
                (ack.getReason() != null ? " (" + ack.getReason() + ")" : ""));

        if (stateMachine != null) {
            CommandState stState = switch (lifecycleState) {
                case EXECUTED -> CommandState.COMPLETED;
                case ACKNOWLEDGED -> CommandState.EDGE_ACKNOWLEDGED;
                default -> CommandState.FAILED;
            };
            try {
                stateMachine.transitionState(correlationId, stState, "ARVACORE",
                        ack.getReason() != null ? ack.getReason() : ack.getStatus(),
                        null, ack.getControllerId(), "CONTROLLER");
            } catch (Exception e) {
                log.debug("State machine transition note: {}", e.getMessage());
            }
        }

        if (alertService != null) {
            String severity = "EXECUTED".equalsIgnoreCase(ack.getStatus()) ? "SUCCESS"
                    : ("RECEIVED".equalsIgnoreCase(ack.getStatus()) ? "INFO" : "WARNING");
            String title = "ArvaCore " + ack.getControllerId() + ": " + ack.getStatus();
            String msg = String.format("Command %s %s on controller %s. Flow: %.1f L/min, Pump: %s.",
                    correlationId, ack.getStatus(), ack.getControllerId(),
                    ack.getCurrentFlowRateLpm() != null ? ack.getCurrentFlowRateLpm() : 0.0,
                    Boolean.TRUE.equals(ack.getPumpActive()) ? "ON" : "OFF");

            alertService.createNotification(
                    "CONTROLLER_ACK",
                    title,
                    msg,
                    severity,
                    "irrigation",
                    ack.getControllerId(),
                    1L,
                    null,
                    null,
                    ack.getControllerId(),
                    "ArvaCore Actuator Subsystem",
                    "ENTIRE FIELD",
                    "Verify physical flow and field sensors.",
                    "ACK:" + correlationId + ":" + ack.getStatus(),
                    10,
                    null
            );
        }

        if (systemEventPublisher != null) {
            try {
                systemEventPublisher.publish(SystemEvent.builder()
                        .eventId(java.util.UUID.randomUUID().toString())
                        .eventType(SystemEventType.IRRIGATION_EXECUTION_UPDATED)
                        .aggregateId(correlationId)
                        .fieldId(1L)
                        .timestamp(LocalDateTime.now())
                        .payload(CommandStatusResponse.builder()
                                .correlationId(correlationId)
                                .fieldId(1L)
                                .operatorId(ack.getControllerId())
                                .status(lifecycleState)
                                .commandType(ack.getAction() != null ? ack.getAction() : "IRRIGATION")
                                .rationale(ack.getReason() != null ? ack.getReason() : ack.getStatus())
                                .details("Flow: " + ack.getCurrentFlowRateLpm() + " L/min, Pump: " + ack.getPumpActive())
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build())
                        .build());
            } catch (Exception e) {
                log.warn("Failed to publish irrigation execution update event: {}", e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of(
                "status", "ACKNOWLEDGED",
                "correlationId", correlationId,
                "recordedState", lifecycleState.name()
        ));
    }
}
