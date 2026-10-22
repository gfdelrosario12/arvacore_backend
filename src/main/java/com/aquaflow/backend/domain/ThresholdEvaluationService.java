package com.aquaflow.backend.domain;

import com.aquaflow.backend.entity.EdgeNode;

public interface ThresholdEvaluationService {
    void evaluateTelemetryThresholds(
            EdgeNode node,
            Double soilMoisture,
            Double waterLevelCm,
            String soilHealth,
            String ultrasonicHealth,
            String telemetryStatus
    );
}
