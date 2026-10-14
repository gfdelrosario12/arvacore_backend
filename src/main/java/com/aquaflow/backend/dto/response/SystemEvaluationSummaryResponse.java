package com.aquaflow.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemEvaluationSummaryResponse {

    private WaterLevelEvaluation waterLevel;
    private SoilMoistureEvaluation soilMoisture;
    private ActivationEvaluation activation;
    private DeactivationEvaluation deactivation;
    private ResponseTimeEvaluation responseTime;
    private WaterConsumptionEvaluation waterConsumption;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaterLevelEvaluation {
        private String capabilityStatus; // "MEASURABLE", "PARTIALLY_MEASURABLE", "NOT_MEASURABLE"
        private String statusDescription;
        private Double latestReportedCm;
        private Double latestReferenceCm;
        private Double meanAbsoluteErrorCm;
        private Double meanAccuracyPercentage;
        private Integer sampleCount;
        private String calculationFormula;
        private List<ReferenceMeasurementResponse> recentComparisons;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SoilMoistureEvaluation {
        private String capabilityStatus;
        private String statusDescription;
        private Double latestReportedPercentage;
        private Double latestReferencePercentage;
        private Double meanAbsoluteError;
        private Double meanAccuracyPercentage;
        private Integer sampleCount;
        private String calculationFormula;
        private String calibrationNotes;
        private List<ReferenceMeasurementResponse> recentComparisons;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivationEvaluation {
        private String capabilityStatus;
        private String statusDescription;
        private Integer totalAttempts;
        private Integer confirmedSuccesses;
        private Integer failedAttempts;
        private Double successRatePercentage;
        private String confirmationSource;
        private String calculationFormula;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeactivationEvaluation {
        private String capabilityStatus;
        private String statusDescription;
        private Integer totalAttempts;
        private Integer confirmedSuccesses;
        private Integer failedAttempts;
        private Double successRatePercentage;
        private String confirmationSource;
        private String calculationFormula;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseTimeEvaluation {
        private String capabilityStatus;
        private String statusDescription;
        private Integer measuredSampleCount;
        private Double avgCommandToAckMs;
        private Double minCommandToAckMs;
        private Double maxCommandToAckMs;
        private Double p95CommandToAckMs;
        private Integer timeoutCount;
        private String clockSyncNotes;
        private String calculationFormula;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaterConsumptionEvaluation {
        private String capabilityStatus;
        private String statusDescription;
        private String measurementType; // "UNAVAILABLE"
        private Double currentFlowRateLpm;
        private Double cumulativeConsumptionLiters;
        private String hardwareRequirement;
        private String missingComponentExplanation;
    }
}

