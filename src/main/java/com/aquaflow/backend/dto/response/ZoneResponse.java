package com.aquaflow.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoneResponse {

    private Long id;
    private String name;
    private String code;
    private Double area;
    private String cropType;
    private Double waterAllocationLimit;
    private Double soilMoisturePercent;
    private Double waterLevelCm;
    private Double waterLevelDistanceCm;
    private List<String> assignedNodeIds;
    private String status;
    private Integer batteryPercent;
    private Boolean isOnline;
    private Long fieldId;
    private LocalDateTime lastUpdated;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}