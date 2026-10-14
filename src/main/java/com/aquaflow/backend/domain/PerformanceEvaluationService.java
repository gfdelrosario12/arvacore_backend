package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.request.ReferenceMeasurementCreateRequest;
import com.aquaflow.backend.dto.response.ReferenceMeasurementResponse;
import com.aquaflow.backend.dto.response.SystemEvaluationSummaryResponse;

import java.util.List;

public interface PerformanceEvaluationService {

    SystemEvaluationSummaryResponse getSystemEvaluationSummary(String nodeId, Long fieldId);

    ReferenceMeasurementResponse recordReferenceMeasurement(ReferenceMeasurementCreateRequest request, String actor);

    List<ReferenceMeasurementResponse> getReferenceMeasurements(String indicatorType, String nodeId);
}

