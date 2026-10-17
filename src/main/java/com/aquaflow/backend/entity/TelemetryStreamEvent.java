package com.aquaflow.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "telemetry_stream_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryStreamEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "node_id", nullable = false, length = 64)
    private String nodeId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "device_timestamp")
    private LocalDateTime deviceTimestamp;

    @Column(name = "sequence_number")
    private Long sequenceNumber;

    @Column(name = "field_id")
    private Long fieldId;

    @Column(name = "zone_id")
    private Long zoneId;

    @Column(name = "zone_code", length = 32)
    private String zoneCode;

    @Column(name = "soil_moisture_percent")
    private Double soilMoisturePercent;

    @Column(name = "water_level_cm")
    private Double waterLevelCm;

    @Column(name = "water_level_distance_cm")
    private Double waterLevelDistanceCm;

    @Column(name = "battery_level")
    private Double batteryLevel;

    @Column(name = "battery_voltage")
    private Double batteryVoltage;

    @Column(name = "rssi_dbm")
    private Integer rssiDbm;

    @Column(name = "snr_db")
    private Double snrDb;

    @Column(name = "readings_count", nullable = false)
    @Builder.Default
    private Integer readingsCount = 0;

    @Column(name = "status_message", length = 500)
    private String statusMessage;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }
    }
}

