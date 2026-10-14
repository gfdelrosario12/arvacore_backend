package com.aquaflow.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferenceMeasurementResponse {
    private Long id;
    private String indicatorType;
    private String nodeId;
    private Long monitoringZoneId;
    private Double reportedValue;
    private Double referenceValue;
    private Double absoluteError;
    private Double percentageError;
    private Double accuracyPercentage;
    private String measurementUnit;
    private String instrumentDetails;
    private String notes;
    private String measuredBy;
    private LocalDateTime measuredAt;
    private LocalDateTime createdAt;
}

