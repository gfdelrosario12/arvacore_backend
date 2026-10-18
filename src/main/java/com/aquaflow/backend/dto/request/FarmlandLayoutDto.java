package com.aquaflow.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarmlandLayoutDto {

    private Long fieldId;
    private String name;
    private Double lengthMeters;
    private Double widthMeters;
    private Double totalArea;
    private String areaUnit;
    private String shapeType;
    private String operationalStatus;
    private String description;
    private String locationAddress;

    @Builder.Default
    private List<MonitoringZoneRequest> zones = new ArrayList<>();
}

