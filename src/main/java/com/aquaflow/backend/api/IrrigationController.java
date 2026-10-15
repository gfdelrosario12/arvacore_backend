package com.aquaflow.backend.api;

import com.aquaflow.backend.domain.EmergencyStopService;
import com.aquaflow.backend.domain.IrrigationDecisionService;
import com.aquaflow.backend.domain.ManualControlService;
import com.aquaflow.backend.dto.request.EmergencyStopRequest;
import com.aquaflow.backend.dto.request.ManualStartRequest;
import com.aquaflow.backend.dto.request.ManualStopRequest;
import com.aquaflow.backend.dto.response.CommandStatusResponse;
import com.aquaflow.backend.dto.response.FieldIrrigationStatusResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/irrigation")
public class IrrigationController {

    private final ManualControlService manualControlService;
    private final EmergencyStopService emergencyStopService;
    private final IrrigationDecisionService decisionService;

    public IrrigationController(ManualControlService manualControlService,
                                EmergencyStopService emergencyStopService,
                                IrrigationDecisionService decisionService) {
        this.manualControlService = manualControlService;
        this.emergencyStopService = emergencyStopService;
        this.decisionService = decisionService;
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
}

