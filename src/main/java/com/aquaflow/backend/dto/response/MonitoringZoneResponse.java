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
public class MonitoringZoneResponse {
    private Long id;
    private String name;
    private Long fieldId;
    private String cropType;
    private Double targetMoisturePercentage;
    private Double waterAllocationLimitLiters;
    private String code;
    private String areaType;
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
    private Boolean isArchived;
    private Integer assignedNodeCount;
    private java.util.List<String> assignedNodeIds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getFieldId() { return fieldId; }
    public void setFieldId(Long fieldId) { this.fieldId = fieldId; }
    public String getCropType() { return cropType; }
    public void setCropType(String cropType) { this.cropType = cropType; }
    public Double getTargetMoisturePercentage() { return targetMoisturePercentage; }
    public void setTargetMoisturePercentage(Double targetMoisturePercentage) { this.targetMoisturePercentage = targetMoisturePercentage; }
    public Double getWaterAllocationLimitLiters() { return waterAllocationLimitLiters; }
    public void setWaterAllocationLimitLiters(Double waterAllocationLimitLiters) { this.waterAllocationLimitLiters = waterAllocationLimitLiters; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

