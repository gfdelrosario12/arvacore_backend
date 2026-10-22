package com.aquaflow.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Lightweight, per-node snapshot of the newest telemetry values known to the backend.
 *
 * <p>Designed for the ArvaLink Home Dashboard, which polls the latest values of every
 * visible node on a fixed interval. It deliberately carries only the fields the
 * dashboard renders (soil moisture, water level, battery, sensor health, connectivity
 * and the telemetry timestamp) so the payload stays small even when the telemetry
 * history table grows to millions of rows.</p>
 *
 * <p>A {@code null} measurement means "the node has never reported this sensor", which is
 * different from a reported numeric {@code 0}. The dashboard renders those two cases
 * differently.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeLatestTelemetryResponse {

    /** Numeric primary key of the edge node. */
    private Long edgeNodeId;

    /** Canonical node identifier, e.g. {@code ARVANODE-001}. */
    private String nodeId;

    private Long monitoringZoneId;
    private String monitoringZoneCode;
    private String monitoringZoneName;

    /** Latest reported soil moisture percentage; {@code null} when never reported. */
    private Double soilMoisturePercent;

    /** Latest reported ultrasonic sensor-to-surface distance in cm; {@code null} when never reported. */
    private Double waterLevelDistanceCm;

    /** Latest derived water depth in cm; {@code null} when never reported. */
    private Double waterLevelCm;

    private Double batteryVoltage;
    private Integer batteryPercent;

    private String soilSensorHealth;
    private String ultrasonicSensorHealth;
    private String telemetryStatus;

    /** Device timestamp of the newest reading backing this snapshot. */
    private LocalDateTime lastTelemetryAt;

    /** Server-side arrival timestamp of the newest reading backing this snapshot. */
    private LocalDateTime lastReceivedAt;

    /** True when the node reported telemetry recently enough to be considered reachable. */
    private Boolean isOnline;

    /** True when at least one telemetry reading backs this snapshot. */
    private Boolean hasTelemetry;
}
