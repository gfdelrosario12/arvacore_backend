package com.aquaflow.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringZoneRequest {

    @NotBlank(message = "Zone name must not be blank")
    private String name;

    private String code;
    private String areaType; // QUARTER, SECTION, RANGE, IRRIGATION_ZONE, CUSTOM
    private String cropType;
    private Double targetMoisturePercentage;
    private Double waterAllocationLimitLiters;
    private Double lengthMeters;
    private Double widthMeters;
    private Double areaSize;
    private String areaUnit;
    private Double relativeX;
    private Double relativeY;
    private Double relativeWidth;
    private Double relativeHeight;
    private String boundaryGeoJson;
    private String operationalStatus;
}

