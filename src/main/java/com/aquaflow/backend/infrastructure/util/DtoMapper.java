package com.aquaflow.backend.infrastructure.util;

import com.aquaflow.backend.dto.response.*;
import com.aquaflow.backend.entity.*;
import org.springframework.stereotype.Component;

@Component
public class DtoMapper {

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
        return AlertResponse.builder()
                .id(alert.getId())
                .deviceId(alert.getDeviceId())
                .zoneId(alert.getZoneId())
                .alertType(alert.getAlertType())
                .alertLevel(alert.getAlertLevel())
                .message(alert.getMessage())
                .acknowledged(alert.isAcknowledged())
                .createdAt(alert.getCreatedAt())
                .acknowledgedAt(alert.getAcknowledgedAt())
                .build();
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
                .createdAt(zone.getCreatedAt())
                .updatedAt(zone.getUpdatedAt())
                .monitoringPoints(pointResponses)
                .assignedNodes(nodeResponses)
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
        String zoneName = node.getMonitoringZone() != null ? node.getMonitoringZone().getName() : null;
        Long fieldId = (node.getMonitoringZone() != null && node.getMonitoringZone().getField() != null)
                ? node.getMonitoringZone().getField().getId() : null;
        String fieldName = (node.getMonitoringZone() != null && node.getMonitoringZone().getField() != null)
                ? node.getMonitoringZone().getField().getName() : null;

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
                .lastHeartbeat(node.getHealthMetrics() != null ? node.getHealthMetrics().getLastHeartbeat() : null)
                .monitoringZoneId(zoneId)
                .monitoringZoneName(zoneName)
                .fieldId(fieldId)
                .fieldName(fieldName)
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
                    // Ultrasonic water-level calculation:
                    // Water Level = Calibrated Sensor-to-Bottom Distance - Measured Sensor-to-Ball Distance
                    // Default calibrated reference is 40 inches (101.6 cm), or adapts to physical installation if greater
                    double calibratedBottom = 101.6;
                    if (distance > calibratedBottom) {
                        calibratedBottom = distance;
                    }
                    double depth = Math.max(0.0, calibratedBottom - distance);
                    // Max measurable water level is 3 feet (36 inches = 91.44 cm)
                    depth = Math.min(depth, 91.44);
                    res.setWaterLevelCm(Math.round(depth * 100.0) / 100.0);
                } else if (r.getSensorType() == com.aquaflow.backend.entity.SensorType.BATTERY_VOLTAGE && res.getBatteryVoltage() == null) {
                    res.setBatteryVoltage(r.getValueNum());
                }
            }
        }
        return res;
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