package com.aquaflow.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelemetryUplinkRequest {

    private String nodeId;
    private String devEui;
    private Long fCnt;
    private Integer fPort;
    private Long seq;
    private String format; // "CHIRPSTACK", "TTN", "JSON"
    private Object payload; // Raw JSON or base64/hex payload string
    private LocalDateTime timestamp;
    private Double batteryLevel;
    private Double batteryVoltage;
    private Double soilMoisturePercent;
    private Double waterLevelDistance;
    private Double solarVoltage;
    private Integer rssiDbm;
    private Double snrDb;
    private String gatewayId;
    private Double frequency;
    private Map<String, Double> measurements;

    public Long getSeq() { return seq; }
    public void setSeq(Long seq) { this.seq = seq; }
    public Double getBatteryVoltage() { return batteryVoltage; }
    public void setBatteryVoltage(Double batteryVoltage) { this.batteryVoltage = batteryVoltage; }
    public Double getSoilMoisturePercent() { return soilMoisturePercent; }
    public void setSoilMoisturePercent(Double soilMoisturePercent) { this.soilMoisturePercent = soilMoisturePercent; }
    public Double getWaterLevelDistance() { return waterLevelDistance; }
    public void setWaterLevelDistance(Double waterLevelDistance) { this.waterLevelDistance = waterLevelDistance; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public String getDevEui() { return devEui; }
    public void setDevEui(String devEui) { this.devEui = devEui; }
    public Long getFCnt() { return fCnt; }
    public void setFCnt(Long fCnt) { this.fCnt = fCnt; }
    public Integer getFPort() { return fPort; }
    public void setFPort(Integer fPort) { this.fPort = fPort; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public Double getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(Double batteryLevel) { this.batteryLevel = batteryLevel; }
    public Double getSolarVoltage() { return solarVoltage; }
    public void setSolarVoltage(Double solarVoltage) { this.solarVoltage = solarVoltage; }
    public Integer getRssiDbm() { return rssiDbm; }
    public void setRssiDbm(Integer rssiDbm) { this.rssiDbm = rssiDbm; }
    public Double getSnrDb() { return snrDb; }
    public void setSnrDb(Double snrDb) { this.snrDb = snrDb; }
    public String getGatewayId() { return gatewayId; }
    public void setGatewayId(String gatewayId) { this.gatewayId = gatewayId; }
    public Double getFrequency() { return frequency; }
    public void setFrequency(Double frequency) { this.frequency = frequency; }
    public Map<String, Double> getMeasurements() { return measurements; }
    public void setMeasurements(Map<String, Double> measurements) { this.measurements = measurements; }
}

