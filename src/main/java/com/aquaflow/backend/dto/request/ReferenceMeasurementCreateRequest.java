package com.aquaflow.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferenceMeasurementCreateRequest {

    @NotBlank(message = "indicatorType is required ('WATER_LEVEL' or 'SOIL_MOISTURE')")
    private String indicatorType;

    @NotBlank(message = "nodeId is required")
    private String nodeId;

    private Long monitoringZoneId;

    @NotNull(message = "reportedValue is required")
    private Double reportedValue;

    @NotNull(message = "referenceValue is required")
    private Double referenceValue;

    @NotBlank(message = "measurementUnit is required")
    private String measurementUnit;

    private String instrumentDetails;

    private String notes;

    private LocalDateTime measuredAt;
}

