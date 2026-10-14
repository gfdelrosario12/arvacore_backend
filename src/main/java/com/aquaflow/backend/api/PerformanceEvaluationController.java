package com.aquaflow.backend.api;

import com.aquaflow.backend.domain.PerformanceEvaluationService;
import com.aquaflow.backend.dto.request.ReferenceMeasurementCreateRequest;
import com.aquaflow.backend.dto.response.ReferenceMeasurementResponse;
import com.aquaflow.backend.dto.response.SystemEvaluationSummaryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/evaluation")
@RequiredArgsConstructor
@Slf4j
public class PerformanceEvaluationController {

    private final PerformanceEvaluationService evaluationService;

    @GetMapping("/summary")
    public ResponseEntity<SystemEvaluationSummaryResponse> getSummary(
            @RequestParam(value = "nodeId", required = false) String nodeId,
            @RequestParam(value = "fieldId", required = false) Long fieldId) {
        log.info("GET /api/v1/evaluation/summary nodeId={}, fieldId={}", nodeId, fieldId);
        return ResponseEntity.ok(evaluationService.getSystemEvaluationSummary(nodeId, fieldId));
    }

    @PostMapping("/reference-measurements")
    public ResponseEntity<ReferenceMeasurementResponse> createReferenceMeasurement(
            @Valid @RequestBody ReferenceMeasurementCreateRequest request,
            Authentication authentication) {
        String actor = (authentication != null) ? authentication.getName() : "OPERATOR";
        log.info("POST /api/v1/evaluation/reference-measurements by {}", actor);
        ReferenceMeasurementResponse response = evaluationService.recordReferenceMeasurement(request, actor);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reference-measurements")
    public ResponseEntity<List<ReferenceMeasurementResponse>> listReferenceMeasurements(
            @RequestParam("indicatorType") String indicatorType,
            @RequestParam(value = "nodeId", required = false) String nodeId) {
        log.info("GET /api/v1/evaluation/reference-measurements indicatorType={}, nodeId={}", indicatorType, nodeId);
        return ResponseEntity.ok(evaluationService.getReferenceMeasurements(indicatorType, nodeId));
    }
}

