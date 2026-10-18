package com.aquaflow.backend.api;

import com.aquaflow.backend.domain.FieldService;
import com.aquaflow.backend.domain.FieldTopologyService;
import com.aquaflow.backend.domain.ZoneAggregationService;
import com.aquaflow.backend.dto.request.FieldRequest;
import com.aquaflow.backend.dto.response.FieldResponse;
import com.aquaflow.backend.dto.response.FieldTopologyResponse;
import com.aquaflow.backend.dto.response.ZoneTelemetryResponse;
import com.aquaflow.backend.dto.response.ZoneTelemetryTrendResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/fields")
public class FieldController {

    private final FieldService fieldService;
    private final FieldTopologyService fieldTopologyService;
    private final ZoneAggregationService zoneAggregationService;
    private final com.aquaflow.backend.domain.ZoneService zoneService;
    private final com.aquaflow.backend.domain.FarmlandValidationService farmlandValidationService;

    @org.springframework.beans.factory.annotation.Autowired
    public FieldController(FieldService fieldService,
                           FieldTopologyService fieldTopologyService,
                           ZoneAggregationService zoneAggregationService,
                           com.aquaflow.backend.domain.ZoneService zoneService,
                           com.aquaflow.backend.domain.FarmlandValidationService farmlandValidationService) {
        this.fieldService = fieldService;
        this.fieldTopologyService = fieldTopologyService;
        this.zoneAggregationService = zoneAggregationService;
        this.zoneService = zoneService;
        this.farmlandValidationService = farmlandValidationService;
    }

    public FieldController(FieldService fieldService,
                           FieldTopologyService fieldTopologyService,
                           ZoneAggregationService zoneAggregationService) {
        this(fieldService, fieldTopologyService, zoneAggregationService, null, null);
    }

    @PostMapping
    public ResponseEntity<FieldResponse> createField(@Valid @RequestBody FieldRequest request) {
        FieldResponse created = fieldService.createField(request);
        return ResponseEntity.created(URI.create("/api/v1/fields/" + created.getId())).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FieldResponse> getFieldById(@PathVariable Long id) {
        return ResponseEntity.ok(fieldService.getFieldById(id));
    }

    @GetMapping
    public ResponseEntity<List<FieldResponse>> getAllFields() {
        return ResponseEntity.ok(fieldService.getAllFields());
    }

    @GetMapping("/page")
    public ResponseEntity<Page<FieldResponse>> getAllFieldsPaged(Pageable pageable) {
        return ResponseEntity.ok(fieldService.getAllFields(pageable));
    }

    @GetMapping("/{id}/topology")
    public ResponseEntity<FieldTopologyResponse> getFieldTopology(@PathVariable Long id) {
        return ResponseEntity.ok(fieldTopologyService.getFieldTopology(id));
    }

    @GetMapping("/{fieldId}/zones/{zoneId}/telemetry/latest")
    public ResponseEntity<ZoneTelemetryResponse> getLatestZoneTelemetry(
            @PathVariable Long fieldId,
            @PathVariable Long zoneId) {
        return ResponseEntity.ok(zoneAggregationService.getLatestZoneTelemetry(zoneId));
    }

    @GetMapping("/{fieldId}/zones/{zoneId}/trend")
    public ResponseEntity<ZoneTelemetryTrendResponse> getZoneTelemetryTrend(
            @PathVariable Long fieldId,
            @PathVariable Long zoneId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return ResponseEntity.ok(zoneAggregationService.getZoneTelemetryTrend(fieldId, zoneId, startTime, endTime));
    }

    @GetMapping("/{id}/readiness")
    public ResponseEntity<com.aquaflow.backend.dto.response.FarmlandValidationResponse> getFieldReadiness(@PathVariable Long id) {
        return ResponseEntity.ok(farmlandValidationService.validateFarmlandReadiness(id));
    }

    @GetMapping("/{id}/zones")
    public ResponseEntity<List<com.aquaflow.backend.dto.response.MonitoringZoneResponse>> getFieldZones(@PathVariable Long id) {
        return ResponseEntity.ok(zoneService.getMonitoringZonesByFieldId(id));
    }

    @PostMapping("/{id}/zones")
    public ResponseEntity<com.aquaflow.backend.dto.response.MonitoringZoneResponse> createZone(
            @PathVariable Long id,
            @Valid @RequestBody com.aquaflow.backend.dto.request.MonitoringZoneRequest request) {
        return ResponseEntity.ok(zoneService.createMonitoringZone(id, request));
    }

    @PutMapping("/{fieldId}/zones/{zoneId}")
    public ResponseEntity<com.aquaflow.backend.dto.response.MonitoringZoneResponse> updateZone(
            @PathVariable Long fieldId,
            @PathVariable Long zoneId,
            @RequestBody com.aquaflow.backend.dto.request.MonitoringZoneRequest request) {
        return ResponseEntity.ok(zoneService.updateMonitoringZone(zoneId, request));
    }

    @DeleteMapping("/{fieldId}/zones/{zoneId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveZone(@PathVariable Long fieldId, @PathVariable Long zoneId) {
        zoneService.archiveMonitoringZone(zoneId);
    }

    @PostMapping("/{fieldId}/zones/{zoneId}/split")
    public ResponseEntity<List<com.aquaflow.backend.dto.response.MonitoringZoneResponse>> splitZone(
            @PathVariable Long fieldId,
            @PathVariable Long zoneId,
            @Valid @RequestBody com.aquaflow.backend.dto.request.SplitZoneRequest request) {
        return ResponseEntity.ok(zoneService.splitMonitoringZone(zoneId, request));
    }

    @PostMapping("/{fieldId}/zones/merge")
    public ResponseEntity<com.aquaflow.backend.dto.response.MonitoringZoneResponse> mergeZones(
            @PathVariable Long fieldId,
            @Valid @RequestBody com.aquaflow.backend.dto.request.MergeZonesRequest request) {
        return ResponseEntity.ok(zoneService.mergeMonitoringZones(fieldId, request));
    }

    @PostMapping("/{fieldId}/layout")
    public ResponseEntity<FieldResponse> updateLayout(
            @PathVariable Long fieldId,
            @RequestBody com.aquaflow.backend.dto.request.FarmlandLayoutDto layout) {
        return ResponseEntity.ok(zoneService.updateFarmlandLayout(fieldId, layout));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<com.aquaflow.backend.dto.response.NodeAssignmentHistoryResponse>> getFieldHistory(@PathVariable Long id) {
        return ResponseEntity.ok(zoneService.getFieldAssignmentHistory(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FieldResponse> updateField(@PathVariable Long id, @Valid @RequestBody FieldRequest request) {
        return ResponseEntity.ok(fieldService.updateField(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteField(@PathVariable Long id) {
        fieldService.deleteField(id);
    }
}

