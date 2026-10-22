package com.aquaflow.backend.infrastructure.util;

import com.aquaflow.backend.dto.response.*;
import com.aquaflow.backend.entity.*;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DtoMapper {

    /** Default calibrated still-well reference distance in cm (40 inches). */
    public static final double DEFAULT_CALIBRATED_BOTTOM_CM = 101.6;

    /** Maximum measurable water depth in cm (3 feet / 36 inches). */
    public static final double MAX_MEASURABLE_WATER_LEVEL_CM = 91.44;

    /**
     * Window used to decide whether a node is currently reachable.
     *
     * <p>The comparison is intentionally performed against server-side timestamps
     * ({@code telemetry_readings.created_at} / {@code edge_nodes.updated_at}) instead of the
     * device clock, because device timestamps follow the local timezone of the field node
     * while the backend runs in UTC. Comparing a device timestamp with
     * {@code LocalDateTime.now()} made every healthy node look permanently offline.</p>
     */
    public static final long ONLINE_WINDOW_MINUTES = 2L;

    /**
     * Derives the water depth in cm from a raw ultrasonic sensor-to-surface distance.
     *
     * <p>A distance of {@code 0} means the node reported a sensor failure placeholder and
     * therefore maps to a depth of {@code 0.0}. A failed or unknown ultrasonic sensor also
     * maps to {@code 0.0} so the dashboard can surface the failure instead of a fabricated
     * depth.</p>
     *
     * @param distanceCm         raw measured distance in cm
     * @param ultrasonicHealth   reported ultrasonic sensor health, may be {@code null}
     * @return water depth in cm, never {@code null}
     */
    public static double computeWaterLevelCm(double distanceCm, String ultrasonicHealth) {
        boolean ultrasonicFailed = distanceCm <= 0.0
                || (ultrasonicHealth != null
                    && !"OK".equalsIgnoreCase(ultrasonicHealth)
                    && !"UNKNOWN".equalsIgnoreCase(ultrasonicHealth));
        if (ultrasonicFailed) {
            return 0.0;
        }
        double calibratedBottom = DEFAULT_CALIBRATED_BOTTOM_CM;
        if (distanceCm > calibratedBottom) {
            calibratedBottom = distanceCm;
        }
        double depth = Math.max(0.0, calibratedBottom - distanceCm);
        depth = Math.min(depth, MAX_MEASURABLE_WATER_LEVEL_CM);
        return Math.round(depth * 100.0) / 100.0;
    }

    /**
     * Resolves the newest server-side activity timestamp for a node.
     *
     * @param node     the edge node
     * @param readings the node's latest readings, may be {@code null} or empty
     * @return the arrival timestamp of the newest reading, otherwise the row update timestamp
     */
    public static LocalDateTime resolveLastActivity(EdgeNode node, java.util.List<TelemetryReading> readings) {
        LocalDateTime lastActivity = null;
        if (readings != null) {
            for (TelemetryReading reading : readings) {
                LocalDateTime receivedAt = reading.getCreatedAt();
                if (receivedAt != null && (lastActivity == null || receivedAt.isAfter(lastActivity))) {
                    lastActivity = receivedAt;
                }
            }
        }
        if (lastActivity == null && node != null) {
            lastActivity = node.getUpdatedAt();
        }
        return lastActivity;
    }

    /**
     * Decides whether a node is currently reachable using server-side timestamps only.
     *
     * @param node     the edge node
     * @param readings the node's latest readings, may be {@code null} or empty
     * @return {@code true} when the node reported activity within the online window
     */
    public static boolean isNodeReachable(EdgeNode node, java.util.List<TelemetryReading> readings) {
        if (node == null) return false;
        NodeLifecycleState lifecycle = node.getLifecycleState();
        if (lifecycle != NodeLifecycleState.ACTIVE && lifecycle != NodeLifecycleState.COMMISSIONED) {
            return false;
        }
        LocalDateTime lastActivity = resolveLastActivity(node, readings);
        return lastActivity != null
                && lastActivity.isAfter(LocalDateTime.now().minusMinutes(ONLINE_WINDOW_MINUTES));
    }

    public static ZoneResponse toZoneResponse(Zone zone) {
        return ZoneResponse.builder()
                .id(zone.getId())
                .name(zone.getName())
                .area(zone.getArea())
                .cropType(zone.getCropType())
                .waterAllocationLimit(zone.getWaterAllocationLimit())
                .createdAt(zone.getCreatedAt())
                .updatedAt(zone.getUpdatedAt())
                .build();
    }

    public static CropResponse toCropResponse(Crop crop) {
        return CropResponse.builder()
                .id(crop.getId())
                .name(crop.getName())
                .waterPerStage(crop.getWaterPerStage())
                .growingSeasonDays(crop.getGrowingSeasonDays())
                .optimalTemperatureMin(crop.getOptimalTemperatureMin())
                .optimalTemperatureMax(crop.getOptimalTemperatureMax())
                .createdAt(crop.getCreatedAt())
                .updatedAt(crop.getUpdatedAt())
                .build();
    }

    public static IrrigationScheduleResponse toScheduleResponse(IrrigationSchedule schedule) {
        return IrrigationScheduleResponse.builder()
                .id(schedule.getId())
                .zoneId(schedule.getZone().getId())
                .startTime(schedule.getStartTime())
                .duration(schedule.getDuration())
                .waterVolume(schedule.getWaterVolume())
                .recurrenceRule(schedule.getRecurrenceRule())
                .status(schedule.getStatus())
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt())
                .build();
    }

    public static DeviceResponse toDeviceResponse(Device device) {
        return DeviceResponse.builder()
                .deviceId(device.getDeviceId())
                .hardwareModel(device.getHardwareModel())
                .firmwareVersion(device.getFirmwareVersion())
                .targetFirmwareVersion(device.getTargetFirmwareVersion())
                .status(device.getStatus())
                .lastHeartbeat(device.getLastHeartbeat())
                .associatedZones(device.getAssociatedZones())
                .createdAt(device.getCreatedAt())
                .updatedAt(device.getUpdatedAt())
                .build();
    }

    public static SensorDataResponse toSensorDataResponse(SensorReading reading) {
        return SensorDataResponse.builder()
                .id(reading.getId())
                .deviceId(reading.getDeviceId())
                .sensorType(reading.getSensorType() != null ? reading.getSensorType().name() : null)
                .value(reading.getValue())
                .unit(reading.getUnit())
                .timestamp(reading.getTimestamp())
                .zoneId(reading.getZoneId())
                .createdAt(reading.getCreatedAt())
                .build();
    }

    public static AlertResponse toAlertResponse(Alert alert) {
        if (alert == null) return null;
        String severity = mapSeverity(alert.getAlertLevel());
        String category = (alert.getCategory() != null && !alert.getCategory().isBlank())
                ? alert.getCategory().toLowerCase()
                : inferCategory(alert.getAlertType());
        String title = (alert.getTitle() != null && !alert.getTitle().isBlank())
                ? alert.getTitle()
                : formatAlertTypeTitle(alert.getAlertType());
        String source = (alert.getSourceName() != null && !alert.getSourceName().isBlank())
                ? alert.getSourceName()
                : (alert.getZoneCode() != null && !alert.getZoneCode().isBlank()
                    ? alert.getZoneCode()
                    : (alert.getDeviceId() != null && !alert.getDeviceId().isBlank()
                        ? "Node " + alert.getDeviceId()
                        : "Centralized Irrigation System"));
        String targetScope = (alert.getTargetScope() != null && !alert.getTargetScope().isBlank())
                ? alert.getTargetScope()
                : (alert.getZoneCode() != null && !alert.getZoneCode().isBlank()
                    ? alert.getZoneCode()
                    : (alert.getDeviceId() != null && !alert.getDeviceId().isBlank()
                        ? alert.getDeviceId()
                        : "ENTIRE FIELD"));
        String recommendedAction = (alert.getRecommendedAction() != null && !alert.getRecommendedAction().isBlank())
                ? alert.getRecommendedAction()
                : "Review system status in the ArvaLink dashboard.";

        return AlertResponse.builder()
                .id(alert.getId())
                .deviceId(alert.getDeviceId())
                .nodeId(alert.getDeviceId())
                .zoneId(alert.getZoneId())
                .fieldId(alert.getFieldId())
                .zoneCode(alert.getZoneCode())
                .alertType(alert.getAlertType())
                .eventType(alert.getAlertType())
                .alertLevel(alert.getAlertLevel())
                .severity(severity)
                .category(category)
                .title(title)
                .message(alert.getMessage())
                .description(alert.getMessage())
                .source(source)
                .targetScope(targetScope)
                .actorId(alert.getActorId())
                .recommendedAction(recommendedAction)
                .acknowledged(alert.isAcknowledged())
                .isRead(alert.isAcknowledged())
                .createdAt(alert.getCreatedAt())
                .timestamp(alert.getCreatedAt())
                .acknowledgedAt(alert.getAcknowledgedAt())
                .readAt(alert.getAcknowledgedAt())
                .metadataJson(alert.getMetadataJson())
                .build();
    }

    private static String mapSeverity(String alertLevel) {
        if (alertLevel == null) return "info";
        String upper = alertLevel.trim().toUpperCase();
        if (upper.contains("CRIT") || upper.contains("ERR") || upper.contains("HIGH") || upper.contains("EMERG")) {
            return "critical";
        }
        if (upper.contains("WARN") || upper.contains("MED") || upper.contains("DEGRAD")) {
            return "warning";
        }
        return "info";
    }

    private static String inferCategory(String alertType) {
        if (alertType == null) return "hardware";
        String upper = alertType.toUpperCase();
        if (upper.contains("IRRIG") || upper.contains("PUMP") || upper.contains("VALVE") || upper.contains("EMERGENCY_STOP") || upper.contains("SCHEDULE")) {
            return "irrigation";
        }
        if (upper.contains("MOISTURE") || upper.contains("WATER_LEVEL") || upper.contains("CROP") || upper.contains("FIELD") || upper.contains("ZONE")) {
            return "agronomic";
        }
        if (upper.contains("OFFLINE") || upper.contains("ONLINE") || upper.contains("SIGNAL") || upper.contains("GATEWAY") || upper.contains("CONNECT")) {
            return "connectivity";
        }
        return "hardware";
    }

    private static String formatAlertTypeTitle(String alertType) {
        if (alertType == null || alertType.isBlank()) return "System Notification";
        String[] words = alertType.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    public static UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public static FieldResponse toFieldResponse(Field field) {
        if (field == null) return null;
        return FieldResponse.builder()
                .id(field.getId())
                .name(field.getName())
                .boundaryGeoJson(field.getBoundaryGeoJson())
                .areaHectares(field.getAreaHectares())
                .lengthMeters(field.getLengthMeters())
                .widthMeters(field.getWidthMeters())
                .totalArea(field.getTotalArea())
                .areaUnit(field.getAreaUnit())
                .shapeType(field.getShapeType())
                .operationalStatus(field.getOperationalStatus())
                .description(field.getDescription())
                .locationAddress(field.getLocationAddress())
                .ownerUsername(field.getOwnerUsername())
                .createdAt(field.getCreatedAt())
                .updatedAt(field.getUpdatedAt())
                .build();
    }

    public static MonitoringZoneResponse toMonitoringZoneResponse(MonitoringZone zone) {
        if (zone == null) return null;
        return MonitoringZoneResponse.builder()
                .id(zone.getId())
                .name(zone.getName())
                .fieldId(zone.getField() != null ? zone.getField().getId() : null)
                .cropType(zone.getCropType())
                .targetMoisturePercentage(zone.getTargetMoisturePercentage())
                .waterAllocationLimitLiters(zone.getWaterAllocationLimitLiters())
                .code(zone.getCode())
                .areaType(zone.getAreaType())
                .lengthMeters(zone.getLengthMeters())
                .widthMeters(zone.getWidthMeters())
                .areaSize(zone.getAreaSize())
                .areaUnit(zone.getAreaUnit())
                .relativeX(zone.getRelativeX())
                .relativeY(zone.getRelativeY())
                .relativeWidth(zone.getRelativeWidth())
                .relativeHeight(zone.getRelativeHeight())
                .boundaryGeoJson(zone.getBoundaryGeoJson())
                .operationalStatus(zone.getOperationalStatus())
                .isArchived(zone.getIsArchived())
                .createdAt(zone.getCreatedAt())
                .updatedAt(zone.getUpdatedAt())
                .build();
    }

    public static MonitoringZoneTopologyResponse toMonitoringZoneTopologyResponse(MonitoringZone zone, java.util.List<MonitoringPoint> points, java.util.List<EdgeNode> nodes) {
        if (zone == null) return null;
        java.util.List<MonitoringPointResponse> pointResponses = points != null ? points.stream().map(DtoMapper::toMonitoringPointResponse).collect(java.util.stream.Collectors.toList()) : java.util.Collections.emptyList();
        java.util.List<EdgeNodeResponse> nodeResponses = nodes != null ? nodes.stream().map(DtoMapper::toEdgeNodeResponse).collect(java.util.stream.Collectors.toList()) : java.util.Collections.emptyList();

        return MonitoringZoneTopologyResponse.builder()
                .id(zone.getId())
                .name(zone.getName())
                .fieldId(zone.getField() != null ? zone.getField().getId() : null)
                .cropType(zone.getCropType())
                .targetMoisturePercentage(zone.getTargetMoisturePercentage())
                .waterAllocationLimitLiters(zone.getWaterAllocationLimitLiters())
                .code(zone.getCode())
                .areaType(zone.getAreaType())
                .lengthMeters(zone.getLengthMeters())
                .widthMeters(zone.getWidthMeters())
                .areaSize(zone.getAreaSize())
                .areaUnit(zone.getAreaUnit())
                .relativeX(zone.getRelativeX())
                .relativeY(zone.getRelativeY())
                .relativeWidth(zone.getRelativeWidth())
                .relativeHeight(zone.getRelativeHeight())
                .operationalStatus(zone.getOperationalStatus())
                .createdAt(zone.getCreatedAt())
                .updatedAt(zone.getUpdatedAt())
                .monitoringPoints(pointResponses)
                .assignedNodes(nodeResponses)
                .build();
    }

    public static NodeAssignmentHistoryResponse toNodeAssignmentHistoryResponse(
            NodeAssignmentHistory history, String fieldName, String zoneName, String zoneCode) {
        if (history == null) return null;
        return NodeAssignmentHistoryResponse.builder()
                .id(history.getId())
                .edgeNodeId(history.getEdgeNodeId())
                .nodeId(history.getNodeId())
                .fieldId(history.getFieldId())
                .fieldName(fieldName)
                .monitoringZoneId(history.getMonitoringZoneId())
                .monitoringZoneName(zoneName)
                .monitoringZoneCode(zoneCode)
                .assignedAt(history.getAssignedAt())
                .unassignedAt(history.getUnassignedAt())
                .assignedBy(history.getAssignedBy())
                .reason(history.getReason())
                .build();
    }

    public static FieldTopologyResponse toFieldTopologyResponse(Field field, java.util.List<MonitoringZoneTopologyResponse> zoneTopologies) {
        if (field == null) return null;
        return FieldTopologyResponse.builder()
                .id(field.getId())
                .name(field.getName())
                .boundaryGeoJson(field.getBoundaryGeoJson())
                .areaHectares(field.getAreaHectares())
                .createdAt(field.getCreatedAt())
                .updatedAt(field.getUpdatedAt())
                .zones(zoneTopologies != null ? zoneTopologies : java.util.Collections.emptyList())
                .build();
    }

    public static EdgeNodeResponse toEdgeNodeResponse(EdgeNode node) {
        if (node == null) return null;
        Long zoneId = node.getMonitoringZone() != null ? node.getMonitoringZone().getId() : null;
        String zoneCode = node.getMonitoringZone() != null
                ? (node.getMonitoringZone().getCode() != null && !node.getMonitoringZone().getCode().isBlank()
                    ? node.getMonitoringZone().getCode()
                    : "Q" + node.getMonitoringZone().getId())
                : null;
        String zoneName = node.getMonitoringZone() != null ? node.getMonitoringZone().getName() : null;
        Long fieldId = (node.getMonitoringZone() != null && node.getMonitoringZone().getField() != null)
                ? node.getMonitoringZone().getField().getId() : null;
        String fieldName = (node.getMonitoringZone() != null && node.getMonitoringZone().getField() != null)
                ? node.getMonitoringZone().getField().getName() : null;

        NodeLifecycleState lifecycle = node.getLifecycleState();
        String pairingStatus;
        if (lifecycle == NodeLifecycleState.ACTIVE
                || lifecycle == NodeLifecycleState.COMMISSIONED
                || lifecycle == NodeLifecycleState.DEGRADED
                || lifecycle == NodeLifecycleState.MAINTENANCE) {
            pairingStatus = "PAIRED";
        } else if (lifecycle == NodeLifecycleState.PROVISIONED) {
            pairingStatus = "PENDING";
        } else {
            pairingStatus = "UNPAIRED";
        }

        String assignmentStatus;
        if (zoneId != null) {
            assignmentStatus = "ASSIGNED_TO_ZONE";
        } else if (fieldId != null) {
            assignmentStatus = "ASSIGNED_TO_FIELD";
        } else {
            assignmentStatus = "UNASSIGNED";
        }

        java.time.LocalDateTime lastHb = node.getHealthMetrics() != null ? node.getHealthMetrics().getLastHeartbeat() : null;
        boolean online = isNodeReachable(node, null);
        String connectivityStatus = online ? "ONLINE" : "OFFLINE";

        String soilHealth = (node.getHealthMetrics() != null && node.getHealthMetrics().getSoilSensorHealth() != null)
                ? node.getHealthMetrics().getSoilSensorHealth() : "UNKNOWN";
        String ultrasonicHealth = (node.getHealthMetrics() != null && node.getHealthMetrics().getUltrasonicSensorHealth() != null)
                ? node.getHealthMetrics().getUltrasonicSensorHealth() : "UNKNOWN";
        String telemetryStatus = (node.getHealthMetrics() != null && node.getHealthMetrics().getTelemetryStatus() != null)
                ? node.getHealthMetrics().getTelemetryStatus() : "UNKNOWN";

        java.util.Map<String, String> sensorHealthMap = new java.util.LinkedHashMap<>();
        sensorHealthMap.put("soilMoisture", soilHealth);
        sensorHealthMap.put("ultrasonic", ultrasonicHealth);

        return EdgeNodeResponse.builder()
                .id(node.getId())
                .nodeId(node.getNodeId())
                .identityType(node.getIdentity() != null && node.getIdentity().getIdentityType() != null ? node.getIdentity().getIdentityType().name() : null)
                .identityValue(node.getIdentity() != null ? node.getIdentity().getIdentityValue() : null)
                .macAddress(node.getIdentity() != null ? node.getIdentity().getMacAddress() : null)
                .serialNumber(node.getIdentity() != null ? node.getIdentity().getSerialNumber() : null)
                .lifecycleState(node.getLifecycleState())
                .healthState(node.getHealthState())
                .batteryLevel(node.getHealthMetrics() != null ? node.getHealthMetrics().getBatteryLevel() : null)
                .solarVoltage(node.getHealthMetrics() != null ? node.getHealthMetrics().getSolarVoltage() : null)
                .signalDbm(node.getHealthMetrics() != null ? node.getHealthMetrics().getSignalDbm() : null)
                .lastHeartbeat(lastHb)
                .monitoringZoneId(zoneId)
                .monitoringZoneCode(zoneCode)
                .monitoringZoneName(zoneName)
                .fieldId(fieldId)
                .fieldName(fieldName)
                .pairingStatus(pairingStatus)
                .assignmentStatus(assignmentStatus)
                .connectivityStatus(connectivityStatus)
                .isOnline(online)
                .soilSensorHealth(soilHealth)
                .ultrasonicSensorHealth(ultrasonicHealth)
                .telemetryStatus(telemetryStatus)
                .sensorHealth(sensorHealthMap)
                .hardwareModel(node.getHardwareModel())
                .firmwareVersion(node.getFirmwareVersion())
                .version(node.getVersion())
                .createdAt(node.getCreatedAt())
                .updatedAt(node.getUpdatedAt())
                .build();
    }

    public static EdgeNodeResponse toEdgeNodeResponse(EdgeNode node, java.util.List<TelemetryReading> readings) {
        if (node == null) return null;
        EdgeNodeResponse res = toEdgeNodeResponse(node);
        if (readings != null && !readings.isEmpty()) {
            for (TelemetryReading r : readings) {
                if (r.getSensorType() == com.aquaflow.backend.entity.SensorType.SOIL_MOISTURE && res.getSoilMoisturePercent() == null) {
                    res.setSoilMoisturePercent(r.getValueNum());
                } else if (r.getSensorType() == com.aquaflow.backend.entity.SensorType.WATER_LEVEL_DISTANCE && res.getWaterLevelCm() == null) {
                    double distance = r.getValueNum();
                    res.setWaterLevelDistanceCm(Math.round(distance * 100.0) / 100.0);
                    res.setWaterLevelCm(computeWaterLevelCm(distance, res.getUltrasonicSensorHealth()));
                } else if (r.getSensorType() == com.aquaflow.backend.entity.SensorType.BATTERY_VOLTAGE && res.getBatteryVoltage() == null) {
                    res.setBatteryVoltage(r.getValueNum());
                }
            }
        }
        // Prefer the arrival timestamp of the newest reading for connectivity: the device
        // clock of the field node does not share a timezone with the backend host.
        boolean reachable = isNodeReachable(node, readings);
        res.setIsOnline(reachable);
        res.setConnectivityStatus(reachable ? "ONLINE" : "OFFLINE");
        return res;
    }

    /**
     * Maps a node together with its latest readings into the lightweight dashboard snapshot.
     *
     * @param node     the edge node
     * @param readings the newest reading per (node, sensor type) pair
     * @return the snapshot, or {@code null} when the node is {@code null}
     */
    public static NodeLatestTelemetryResponse toNodeLatestTelemetryResponse(
            EdgeNode node,
            java.util.List<TelemetryReading> readings) {
        if (node == null) return null;

        Double soilMoisturePercent = null;
        Double waterLevelDistanceCm = null;
        Double batteryVoltage = null;
        LocalDateTime lastTelemetryAt = null;
        LocalDateTime lastReceivedAt = null;

        if (readings != null) {
            for (TelemetryReading r : readings) {
                if (r.getSensorType() == com.aquaflow.backend.entity.SensorType.SOIL_MOISTURE
                        && soilMoisturePercent == null) {
                    soilMoisturePercent = r.getValueNum();
                } else if (r.getSensorType() == com.aquaflow.backend.entity.SensorType.WATER_LEVEL_DISTANCE
                        && waterLevelDistanceCm == null) {
                    waterLevelDistanceCm = Math.round(r.getValueNum() * 100.0) / 100.0;
                } else if (r.getSensorType() == com.aquaflow.backend.entity.SensorType.BATTERY_VOLTAGE
                        && batteryVoltage == null) {
                    batteryVoltage = r.getValueNum();
                }
                if (r.getTimestamp() != null && (lastTelemetryAt == null || r.getTimestamp().isAfter(lastTelemetryAt))) {
                    lastTelemetryAt = r.getTimestamp();
                }
                if (r.getCreatedAt() != null && (lastReceivedAt == null || r.getCreatedAt().isAfter(lastReceivedAt))) {
                    lastReceivedAt = r.getCreatedAt();
                }
            }
        }

        String ultrasonicHealth = (node.getHealthMetrics() != null && node.getHealthMetrics().getUltrasonicSensorHealth() != null)
                ? node.getHealthMetrics().getUltrasonicSensorHealth() : "UNKNOWN";
        String soilHealth = (node.getHealthMetrics() != null && node.getHealthMetrics().getSoilSensorHealth() != null)
                ? node.getHealthMetrics().getSoilSensorHealth() : "UNKNOWN";
        String telemetryStatus = (node.getHealthMetrics() != null && node.getHealthMetrics().getTelemetryStatus() != null)
                ? node.getHealthMetrics().getTelemetryStatus() : "UNKNOWN";

        Double waterLevelCm = null;
        if (waterLevelDistanceCm != null) {
            waterLevelCm = computeWaterLevelCm(waterLevelDistanceCm, ultrasonicHealth);
        }

        Double batteryLevel = (node.getHealthMetrics() != null) ? node.getHealthMetrics().getBatteryLevel() : null;

        return NodeLatestTelemetryResponse.builder()
                .edgeNodeId(node.getId())
                .nodeId(node.getNodeId())
                .monitoringZoneId(node.getMonitoringZone() != null ? node.getMonitoringZone().getId() : null)
                .monitoringZoneCode(node.getMonitoringZone() != null
                        ? (node.getMonitoringZone().getCode() != null && !node.getMonitoringZone().getCode().isBlank()
                            ? node.getMonitoringZone().getCode()
                            : "Q" + node.getMonitoringZone().getId())
                        : null)
                .monitoringZoneName(node.getMonitoringZone() != null ? node.getMonitoringZone().getName() : null)
                .soilMoisturePercent(soilMoisturePercent)
                .waterLevelDistanceCm(waterLevelDistanceCm)
                .waterLevelCm(waterLevelCm)
                .batteryVoltage(batteryVoltage)
                .batteryPercent(batteryLevel != null ? (int) Math.round(batteryLevel) : null)
                .soilSensorHealth(soilHealth)
                .ultrasonicSensorHealth(ultrasonicHealth)
                .telemetryStatus(telemetryStatus)
                .lastTelemetryAt(lastTelemetryAt)
                .lastReceivedAt(lastReceivedAt)
                .isOnline(isNodeReachable(node, readings))
                .hasTelemetry(readings != null && !readings.isEmpty())
                .build();
    }

    public static MonitoringPointResponse toMonitoringPointResponse(MonitoringPoint point) {
        if (point == null) return null;
        return MonitoringPointResponse.builder()
                .id(point.getId())
                .name(point.getName())
                .monitoringZoneId(point.getMonitoringZone() != null ? point.getMonitoringZone().getId() : null)
                .edgeNodeId(point.getEdgeNode() != null ? point.getEdgeNode().getId() : null)
                .primarySensorType(point.getPrimarySensorType())
                .depthCm(point.getDepthCm())
                .latitude(point.getLatitude())
                .longitude(point.getLongitude())
                .createdAt(point.getCreatedAt())
                .updatedAt(point.getUpdatedAt())
                .build();
    }

    public static TelemetryReadingResponse toTelemetryReadingResponse(TelemetryReading reading) {
        if (reading == null) return null;
        return TelemetryReadingResponse.builder()
                .id(reading.getId())
                .monitoringPointId(reading.getMonitoringPoint() != null ? reading.getMonitoringPoint().getId() : null)
                .edgeNodeId(reading.getEdgeNode() != null ? reading.getEdgeNode().getId() : null)
                .sensorType(reading.getSensorType())
                .valueNum(reading.getValueNum())
                .unit(reading.getUnit())
                .timestamp(reading.getTimestamp())
                .createdAt(reading.getCreatedAt())
                .build();
    }

    public static IrrigationDecisionResponse toIrrigationDecisionResponse(IrrigationDecision decision) {
        if (decision == null) return null;
        return IrrigationDecisionResponse.builder()
                .id(decision.getId())
                .edgeNodeId(decision.getEdgeNode() != null ? decision.getEdgeNode().getId() : null)
                .nodeId(decision.getEdgeNode() != null ? decision.getEdgeNode().getNodeId() : null)
                .decisionType(decision.getDecisionType())
                .triggerReason(decision.getTriggerReason())
                .cropStage(decision.getCropStage())
                .confidence(decision.getConfidence())
                .requestedDurationMinutes(decision.getRequestedDurationMinutes())
                .requestedVolumeLiters(decision.getRequestedVolumeLiters())
                .actualDurationMinutes(decision.getActualDurationMinutes())
                .executionStatus(decision.getExecutionStatus())
                .failureReason(decision.getFailureReason())
                .configVersion(decision.getConfigVersion())
                .correlationId(decision.getCorrelationId())
                .telemetryWaterLevelCm(decision.getTelemetryWaterLevelCm())
                .telemetrySoilMoisturePercent(decision.getTelemetrySoilMoisturePercent())
                .telemetryTemperatureC(decision.getTelemetryTemperatureC())
                .batteryPercentage(decision.getBatteryPercentage())
                .rssi(decision.getRssi())
                .snr(decision.getSnr())
                .nodeTimestamp(decision.getNodeTimestamp())
                .createdAt(decision.getCreatedAt())
                .build();
    }

    public static AwdThresholdConfigResponse toAwdThresholdConfigResponse(AwdThresholdConfig config) {
        if (config == null) return null;
        return AwdThresholdConfigResponse.builder()
                .id(config.getId())
                .growthStage(config.getGrowthStage())
                .triggerMoisturePercentage(config.getTriggerMoisturePercentage())
                .targetMoisturePercentage(config.getTargetMoisturePercentage())
                .targetFloodDepthCm(config.getTargetFloodDepthCm())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    public static AutoIrrigationConfigResponse toAutoIrrigationConfigResponse(AutoIrrigationConfig config) {
        if (config == null) return null;
        java.util.List<AwdThresholdConfigResponse> thresholds = config.getThresholds() != null
                ? config.getThresholds().stream().map(DtoMapper::toAwdThresholdConfigResponse).toList()
                : java.util.Collections.emptyList();

        return AutoIrrigationConfigResponse.builder()
                .id(config.getId())
                .fieldId(config.getField() != null ? config.getField().getId() : null)
                .edgeNodeId(config.getEdgeNode() != null ? config.getEdgeNode().getId() : null)
                .enabled(config.getEnabled())
                .maxDurationMinutes(config.getMaxDurationMinutes())
                .minCooldownMinutes(config.getMinCooldownMinutes())
                .allowedStartHour(config.getAllowedStartHour())
                .allowedEndHour(config.getAllowedEndHour())
                .targetFloodDepthCm(config.getTargetFloodDepthCm())
                .rainDelayHours(config.getRainDelayHours())
                .minConfidenceThreshold(config.getMinConfidenceThreshold())
                .configVersion(config.getConfigVersion())
                .updatedBy(config.getUpdatedBy())
                .changeReason(config.getChangeReason())
                .thresholds(thresholds)
                .scheduleMode(config.getScheduleMode())
                .minSoilMoisturePercentage(config.getMinSoilMoisturePercentage())
                .maxSoilMoisturePercentage(config.getMaxSoilMoisturePercentage())
                .maxSingleRunMinutes(config.getMaxSingleRunMinutes())
                .safetyRainOverride(config.getSafetyRainOverride())
                .cronSchedule(config.getCronSchedule())
                .version(config.getVersion())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    public static IrrigationAuditLogResponse toIrrigationAuditLogResponse(IrrigationAuditLog log) {
        if (log == null) return null;
        return IrrigationAuditLogResponse.builder()
                .id(log.getId())
                .eventType(log.getEventType())
                .actor(log.getActor())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .payloadJson(log.getPayloadJson())
                .createdAt(log.getCreatedAt())
                .build();
    }
}