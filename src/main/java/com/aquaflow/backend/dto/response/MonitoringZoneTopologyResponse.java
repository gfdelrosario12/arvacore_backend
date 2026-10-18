package com.aquaflow.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringZoneTopologyResponse {
    private Long id;
    private String name;
    private Long fieldId;
    private String cropType;
    private String cropGrowthStage;
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
    private String operationalStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private List<MonitoringPointResponse> monitoringPoints = new ArrayList<>();

    @Builder.Default
    private List<EdgeNodeResponse> assignedNodes = new ArrayList<>();

    private ZoneTelemetryResponse telemetrySummary;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getFieldId() { return fieldId; }
    public void setFieldId(Long fieldId) { this.fieldId = fieldId; }
    public String getCropType() { return cropType; }
    public void setCropType(String cropType) { this.cropType = cropType; }
    public String getCropGrowthStage() { return cropGrowthStage; }
    public void setCropGrowthStage(String cropGrowthStage) { this.cropGrowthStage = cropGrowthStage; }
    public Double getTargetMoisturePercentage() { return targetMoisturePercentage; }
    public void setTargetMoisturePercentage(Double targetMoisturePercentage) { this.targetMoisturePercentage = targetMoisturePercentage; }
    public Double getWaterAllocationLimitLiters() { return waterAllocationLimitLiters; }
    public void setWaterAllocationLimitLiters(Double waterAllocationLimitLiters) { this.waterAllocationLimitLiters = waterAllocationLimitLiters; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<MonitoringPointResponse> getMonitoringPoints() { return monitoringPoints; }
    public void setMonitoringPoints(List<MonitoringPointResponse> monitoringPoints) { this.monitoringPoints = monitoringPoints; }
    public List<EdgeNodeResponse> getAssignedNodes() { return assignedNodes; }
    public void setAssignedNodes(List<EdgeNodeResponse> assignedNodes) { this.assignedNodes = assignedNodes; }
    public ZoneTelemetryResponse getTelemetrySummary() { return telemetrySummary; }
    public void setTelemetrySummary(ZoneTelemetryResponse telemetrySummary) { this.telemetrySummary = telemetrySummary; }
}

