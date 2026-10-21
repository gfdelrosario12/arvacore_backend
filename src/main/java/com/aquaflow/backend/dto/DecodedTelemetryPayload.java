package com.aquaflow.backend.dto;

import com.aquaflow.backend.entity.SensorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecodedTelemetryPayload {

    private String nodeId;
    private String devEui;
    private Long fCnt;
    private Integer fPort;
    private LocalDateTime timestamp;
    private Double batteryLevel;
    private Double solarVoltage;
    private SignalMetadataDto signalMetadata;

    @Builder.Default
    private Map<SensorType, Double> measurements = new HashMap<>();
    @Builder.Default
    private Map<String, String> sensorHealth = new HashMap<>();
    private String telemetryStatus;
    private String rawPayload;

    public Map<String, String> getSensorHealth() { return sensorHealth; }
    public void setSensorHealth(Map<String, String> sensorHealth) { this.sensorHealth = sensorHealth; }
    public String getTelemetryStatus() { return telemetryStatus; }
    public void setTelemetryStatus(String telemetryStatus) { this.telemetryStatus = telemetryStatus; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public String getDevEui() { return devEui; }
    public void setDevEui(String devEui) { this.devEui = devEui; }
    public Long getFCnt() { return fCnt; }
    public void setFCnt(Long fCnt) { this.fCnt = fCnt; }
    public Integer getFPort() { return fPort; }
    public void setFPort(Integer fPort) { this.fPort = fPort; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public Double getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(Double batteryLevel) { this.batteryLevel = batteryLevel; }
    public Double getSolarVoltage() { return solarVoltage; }
    public void setSolarVoltage(Double solarVoltage) { this.solarVoltage = solarVoltage; }
    public SignalMetadataDto getSignalMetadata() { return signalMetadata; }
    public void setSignalMetadata(SignalMetadataDto signalMetadata) { this.signalMetadata = signalMetadata; }
    public Map<SensorType, Double> getMeasurements() { return measurements; }
    public void setMeasurements(Map<SensorType, Double> measurements) { this.measurements = measurements; }
    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }
}

