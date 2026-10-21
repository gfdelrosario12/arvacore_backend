package com.aquaflow.backend.dto.response;

import com.aquaflow.backend.entity.TelemetryStreamEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryStreamEventResponse {

    private String eventId;
    private String nodeId;
    private String eventType;
    private String status;
    private LocalDateTime receivedAt;
    private LocalDateTime deviceTimestamp;
    private Long sequenceNumber;
    private Long fieldId;
    private Long zoneId;
    private String zoneCode;
    private Double soilMoisturePercent;
    private Double waterLevelCm;
    private Double waterLevelDistanceCm;
    private Double batteryLevel;
    private Double batteryVoltage;
    private Integer rssiDbm;
    private Double snrDb;
    private Integer readingsCount;
    private String soilSensorHealth;
    private String ultrasonicSensorHealth;
    private String telemetryStatus;
    private String statusMessage;
    private String rawPayload;

    public static TelemetryStreamEventResponse fromEntity(TelemetryStreamEvent event) {
        if (event == null) return null;
        return TelemetryStreamEventResponse.builder()
                .eventId(event.getEventId())
                .nodeId(event.getNodeId())
                .eventType(event.getEventType())
                .status(event.getStatus())
                .receivedAt(event.getReceivedAt())
                .deviceTimestamp(event.getDeviceTimestamp())
                .sequenceNumber(event.getSequenceNumber())
                .fieldId(event.getFieldId())
                .zoneId(event.getZoneId())
                .zoneCode(event.getZoneCode())
                .soilMoisturePercent(event.getSoilMoisturePercent())
                .waterLevelCm(event.getWaterLevelCm())
                .waterLevelDistanceCm(event.getWaterLevelDistanceCm())
                .batteryLevel(event.getBatteryLevel())
                .batteryVoltage(event.getBatteryVoltage())
                .rssiDbm(event.getRssiDbm())
                .snrDb(event.getSnrDb())
                .readingsCount(event.getReadingsCount())
                .soilSensorHealth(event.getSoilSensorHealth())
                .ultrasonicSensorHealth(event.getUltrasonicSensorHealth())
                .telemetryStatus(event.getTelemetryStatus())
                .statusMessage(event.getStatusMessage())
                .rawPayload(event.getRawPayload())
                .build();
    }
}

