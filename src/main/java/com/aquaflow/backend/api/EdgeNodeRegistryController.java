package com.aquaflow.backend.api;

import com.aquaflow.backend.domain.EdgeNodeRegistryService;
import com.aquaflow.backend.dto.request.*;
import com.aquaflow.backend.dto.response.*;
import com.aquaflow.backend.entity.HealthState;
import com.aquaflow.backend.entity.NodeLifecycleState;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/nodes")
@RequiredArgsConstructor
public class EdgeNodeRegistryController {

    private final EdgeNodeRegistryService edgeNodeRegistryService;

    @PostMapping
    public ResponseEntity<EdgeNodeResponse> registerNode(@Valid @RequestBody RegisterNodeRequest request) {
        EdgeNodeResponse response = edgeNodeRegistryService.registerNode(request);
        return ResponseEntity.created(URI.create("/api/v1/nodes/" + response.getId())).body(response);
    }

    @GetMapping
    public ResponseEntity<List<EdgeNodeResponse>> getNodes(
            @RequestParam(required = false) NodeLifecycleState lifecycleState,
            @RequestParam(required = false) HealthState healthState,
            @RequestParam(required = false) Long zoneId) {
        return ResponseEntity.ok(edgeNodeRegistryService.getNodes(lifecycleState, healthState, zoneId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EdgeNodeResponse> getNodeById(@PathVariable Long id) {
        return ResponseEntity.ok(edgeNodeRegistryService.getNodeById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EdgeNodeResponse> updateNode(@PathVariable Long id, @RequestBody RegisterNodeRequest request) {
        return ResponseEntity.ok(edgeNodeRegistryService.updateNode(id, request));
    }

    @PutMapping("/{id}/field-assignment")
    public ResponseEntity<EdgeNodeResponse> assignNodeToField(
            @PathVariable String id,
            @Valid @RequestBody AssignNodeFieldRequest request) {
        return ResponseEntity.ok(edgeNodeRegistryService.assignNodeToField(id, request));
    }

    @PostMapping("/{id}/commission")
    public ResponseEntity<CommissionNodeResponse> commissionNode(
            @PathVariable Long id,
            @RequestBody(required = false) CommissionNodeRequest request) {
        return ResponseEntity.ok(edgeNodeRegistryService.commissionNode(id, request));
    }

    @PostMapping("/{id}/decommission")
    public ResponseEntity<EdgeNodeResponse> decommissionNode(@PathVariable Long id) {
        return ResponseEntity.ok(edgeNodeRegistryService.decommissionNode(id));
    }

    @PostMapping("/{id}/replace")
    public ResponseEntity<EdgeNodeResponse> replaceNode(
            @PathVariable Long id,
            @Valid @RequestBody ReplaceNodeRequest request) {
        return ResponseEntity.ok(edgeNodeRegistryService.replaceNode(id, request));
    }

    @PutMapping("/{id}/transmission")
    public ResponseEntity<EdgeNodeResponse> updateTransmission(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTransmissionRequest request) {
        return ResponseEntity.ok(edgeNodeRegistryService.updateTransmissionConfig(id, request));
    }

    @GetMapping("/{id}/health")
    public ResponseEntity<NodeHealthResponse> getNodeHealth(@PathVariable Long id) {
        return ResponseEntity.ok(edgeNodeRegistryService.getNodeHealth(id));
    }

    @GetMapping("/{id}/telemetry/latest")
    public ResponseEntity<List<TelemetryReadingResponse>> getLatestTelemetry(@PathVariable Long id) {
        return ResponseEntity.ok(edgeNodeRegistryService.getLatestTelemetry(id));
    }

    @GetMapping("/{id}/telemetry/history")
    public ResponseEntity<Page<TelemetryReadingResponse>> getHistoricalTelemetry(
            @PathVariable Long id,
            Pageable pageable) {
        return ResponseEntity.ok(edgeNodeRegistryService.getHistoricalTelemetry(id, pageable));
    }
}

