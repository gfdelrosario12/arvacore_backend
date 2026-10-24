package com.aquaflow.backend.api;

import com.aquaflow.backend.domain.ArvaGateService;
import com.aquaflow.backend.dto.request.ArvaGateAckRequest;
import com.aquaflow.backend.dto.request.ArvaGateDispatchCommandRequest;
import com.aquaflow.backend.dto.request.ArvaGateTelemetryRequest;
import com.aquaflow.backend.dto.response.ArvaGateStatusResponse;
import com.aquaflow.backend.entity.ArvaGateCommand;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/gates")
public class ArvaGateController {

    private static final Logger log = LoggerFactory.getLogger(ArvaGateController.class);

    private final ArvaGateService arvaGateService;

    public ArvaGateController(ArvaGateService arvaGateService) {
        this.arvaGateService = arvaGateService;
    }

    /**
     * Frontend / App: Fetch full status of ArvaGate device, 3 physical gates, and recent events.
     */
    @GetMapping("/status/{deviceId}")
    public ResponseEntity<ArvaGateStatusResponse> getGateStatus(@PathVariable String deviceId) {
        return ResponseEntity.ok(arvaGateService.getStatus(deviceId));
    }

    /**
     * Frontend / Operator: Dispatch manual or automated gate open/close/stop command.
     */
    @PostMapping("/command/{deviceId}")
    public ResponseEntity<ArvaGateCommand> dispatchGateCommand(
            @PathVariable String deviceId,
            @Valid @RequestBody ArvaGateDispatchCommandRequest request,
            Authentication auth) {
        String actor = (auth != null && auth.getName() != null) ? auth.getName() : "OPERATOR";
        ArvaGateCommand command = arvaGateService.dispatchCommand(deviceId, request, actor);
        return ResponseEntity.accepted().body(command);
    }

    /**
     * Hardware Controller: Polls for pending gate commands.
     */
    @GetMapping("/pending-command/{deviceId}")
    public ResponseEntity<ArvaGateCommand> getPendingCommand(@PathVariable String deviceId) {
        ArvaGateCommand cmd = arvaGateService.getPendingCommand(deviceId);
        if (cmd == null) {
            return ResponseEntity.ok(ArvaGateCommand.builder().build());
        }
        return ResponseEntity.ok(cmd);
    }

    /**
     * Hardware Controller: Acknowledges command execution result.
     */
    @PostMapping("/ack/{commandId}")
    public ResponseEntity<Map<String, Object>> acknowledgeGateCommand(
            @PathVariable String commandId,
            @Valid @RequestBody ArvaGateAckRequest ack) {
        arvaGateService.handleAcknowledgement(commandId, ack);
        return ResponseEntity.ok(Map.of("status", "ACKNOWLEDGED", "commandId", commandId));
    }

    /**
     * Hardware Controller: Uplinks periodic gate operational state and limit switch feedback.
     */
    @PostMapping("/telemetry/{deviceId}")
    public ResponseEntity<Map<String, Object>> uplinkGateTelemetry(
            @PathVariable String deviceId,
            @Valid @RequestBody ArvaGateTelemetryRequest telemetry) {
        arvaGateService.handleTelemetry(deviceId, telemetry);
        return ResponseEntity.ok(Map.of("status", "RECEIVED", "deviceId", deviceId));
    }
}

