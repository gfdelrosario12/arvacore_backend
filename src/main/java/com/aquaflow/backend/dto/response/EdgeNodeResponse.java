package com.aquaflow.backend.dto.response;

import com.aquaflow.backend.entity.HealthState;
import com.aquaflow.backend.entity.NodeLifecycleState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EdgeNodeResponse {
    private Long id;
    private String nodeId;
    private String identityType;
    private String identityValue;
    private String macAddress;
    private String serialNumber;
    private NodeLifecycleState lifecycleState;
    private HealthState healthState;
    private Double batteryLevel;
    private Double batteryVoltage;
    private Double solarVoltage;
    private Integer signalDbm;
    private Double soilMoisturePercent;
    private Double waterLevelCm;
    private LocalDateTime lastHeartbeat;
    private Long monitoringZoneId;
    private String monitoringZoneName;
    private Long fieldId;
    private String fieldName;
    private String hardwareModel;
    private String firmwareVersion;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Double getSoilMoisturePercent() { return soilMoisturePercent; }
    public void setSoilMoisturePercent(Double soilMoisturePercent) { this.soilMoisturePercent = soilMoisturePercent; }
    public Double getWaterLevelCm() { return waterLevelCm; }
    public void setWaterLevelCm(Double waterLevelCm) { this.waterLevelCm = waterLevelCm; }
    public Double getBatteryVoltage() { return batteryVoltage; }
    public void setBatteryVoltage(Double batteryVoltage) { this.batteryVoltage = batteryVoltage; }

    public String getMonitoringZoneName() { return monitoringZoneName; }
    public void setMonitoringZoneName(String monitoringZoneName) { this.monitoringZoneName = monitoringZoneName; }
    public Long getFieldId() { return fieldId; }
    public void setFieldId(Long fieldId) { this.fieldId = fieldId; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public String getIdentityType() { return identityType; }
    public void setIdentityType(String identityType) { this.identityType = identityType; }
    public String getIdentityValue() { return identityValue; }
    public void setIdentityValue(String identityValue) { this.identityValue = identityValue; }
    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public NodeLifecycleState getLifecycleState() { return lifecycleState; }
    public void setLifecycleState(NodeLifecycleState lifecycleState) { this.lifecycleState = lifecycleState; }
    public HealthState getHealthState() { return healthState; }
    public void setHealthState(HealthState healthState) { this.healthState = healthState; }
    public Double getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(Double batteryLevel) { this.batteryLevel = batteryLevel; }
    public Double getSolarVoltage() { return solarVoltage; }
    public void setSolarVoltage(Double solarVoltage) { this.solarVoltage = solarVoltage; }
    public Integer getSignalDbm() { return signalDbm; }
    public void setSignalDbm(Integer signalDbm) { this.signalDbm = signalDbm; }
    public LocalDateTime getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(LocalDateTime lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
    public Long getMonitoringZoneId() { return monitoringZoneId; }
    public void setMonitoringZoneId(Long monitoringZoneId) { this.monitoringZoneId = monitoringZoneId; }
    public String getHardwareModel() { return hardwareModel; }
    public void setHardwareModel(String hardwareModel) { this.hardwareModel = hardwareModel; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

