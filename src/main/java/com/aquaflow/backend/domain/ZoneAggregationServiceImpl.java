package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.response.ZoneTelemetryResponse;
import com.aquaflow.backend.dto.response.ZoneTelemetryTrendPoint;
import com.aquaflow.backend.dto.response.ZoneTelemetryTrendResponse;
import com.aquaflow.backend.entity.*;
import com.aquaflow.backend.infrastructure.exception.ResourceNotFoundException;
import com.aquaflow.backend.persistence.EdgeNodeRepository;
import com.aquaflow.backend.persistence.MonitoringZoneRepository;
import com.aquaflow.backend.persistence.TelemetryReadingRepository;
import com.aquaflow.backend.persistence.ZoneTelemetryAggregateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ZoneAggregationServiceImpl implements ZoneAggregationService {

    private static final Logger log = LoggerFactory.getLogger(ZoneAggregationServiceImpl.class);

    private static final int STALE_THRESHOLD_MINUTES = 30;
    private static final int OFFLINE_THRESHOLD_MINUTES = 60;
    private static final int CACHE_VALIDITY_MINUTES = 5;

    private final MonitoringZoneRepository monitoringZoneRepository;
    private final EdgeNodeRepository edgeNodeRepository;
    private final TelemetryReadingRepository telemetryReadingRepository;
    private final ZoneTelemetryAggregateRepository zoneTelemetryAggregateRepository;

    public ZoneAggregationServiceImpl(MonitoringZoneRepository monitoringZoneRepository,
                                       EdgeNodeRepository edgeNodeRepository,
                                       TelemetryReadingRepository telemetryReadingRepository,
                                       ZoneTelemetryAggregateRepository zoneTelemetryAggregateRepository) {
        this.monitoringZoneRepository = monitoringZoneRepository;
        this.edgeNodeRepository = edgeNodeRepository;
        this.telemetryReadingRepository = telemetryReadingRepository;
        this.zoneTelemetryAggregateRepository = zoneTelemetryAggregateRepository;
    }

    @Override
    @Transactional
    public ZoneTelemetryResponse calculateAndCacheZoneTelemetry(Long zoneId) {
        MonitoringZone zone = monitoringZoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("MonitoringZone not found with id: " + zoneId));

        Long fieldId = zone.getField() != null ? zone.getField().getId() : null;

        List<EdgeNode> nodes = edgeNodeRepository.findByMonitoringZoneId(zoneId);
        int totalNodes = nodes.size();

        if (nodes.isEmpty()) {
            ZoneTelemetryAggregate aggregate = ZoneTelemetryAggregate.builder()
                    .zoneId(zoneId)
                    .fieldId(fieldId != null ? fieldId : 0L)
                    .totalNodes(0)
                    .onlineNodes(0)
                    .staleNodes(0)
                    .offlineNodes(0)
                    .healthStatus(ZoneHealthStatus.OFFLINE)
                    .calculatedAt(LocalDateTime.now())
                    .build();

            aggregate = zoneTelemetryAggregateRepository.save(aggregate);
            return mapToResponse(aggregate);
        }

        LocalDateTime now = LocalDateTime.now();
        int onlineNodes = 0;
        int staleNodes = 0;
        int offlineNodes = 0;

        List<Double> soilMoistureValues = new ArrayList<>();
        List<Double> temperatureValues = new ArrayList<>();
        List<Double> humidityValues = new ArrayList<>();
        List<Double> waterLevelValues = new ArrayList<>();
        List<Double> batteryValues = new ArrayList<>();
        List<Integer> rssiValues = new ArrayList<>();
        List<Double> snrValues = new ArrayList<>();

        for (EdgeNode node : nodes) {
            LocalDateTime lastSeen = null;
            if (node.getHealthMetrics() != null && node.getHealthMetrics().getLastHeartbeat() != null) {
                lastSeen = node.getHealthMetrics().getLastHeartbeat();
            }

            boolean isCommissioned = node.getLifecycleState() == NodeLifecycleState.COMMISSIONED;
            boolean isCriticalHealth = node.getHealthState() == HealthState.CRITICAL;

            List<TelemetryReading> readings = telemetryReadingRepository.findByEdgeNodeIdOrderByTimestampDesc(node.getId());
            TelemetryReading latestReading = readings.isEmpty() ? null : readings.get(0);

            if (latestReading != null && (lastSeen == null || latestReading.getTimestamp().isAfter(lastSeen))) {
                lastSeen = latestReading.getTimestamp();
            }

            if (!isCommissioned || isCriticalHealth || lastSeen == null || lastSeen.isBefore(now.minusMinutes(OFFLINE_THRESHOLD_MINUTES))) {
                offlineNodes++;
            } else if (lastSeen.isBefore(now.minusMinutes(STALE_THRESHOLD_MINUTES))) {
                staleNodes++;
            } else {
                onlineNodes++;
            }

            if (node.getHealthMetrics() != null) {
                if (node.getHealthMetrics().getBatteryLevel() != null) {
                    batteryValues.add(node.getHealthMetrics().getBatteryLevel());
                }
                if (node.getHealthMetrics().getSignalDbm() != null) {
                    rssiValues.add(node.getHealthMetrics().getSignalDbm());
                }
            }

            if (latestReading != null && !readings.isEmpty()) {
                for (TelemetryReading r : readings) {
                    if (r.getTimestamp().isAfter(now.minusMinutes(STALE_THRESHOLD_MINUTES))) {
                        if (r.getSensorType() == SensorType.SOIL_MOISTURE) {
                            soilMoistureValues.add(r.getValueNum());
                        } else if (r.getSensorType() == SensorType.TEMPERATURE) {
                            temperatureValues.add(r.getValueNum());
                        } else if (r.getSensorType() == SensorType.HUMIDITY) {
                            humidityValues.add(r.getValueNum());
                        } else if (r.getSensorType() == SensorType.WATER_LEVEL_DISTANCE) {
                            // JSN-SR04T ultrasonic sensor measures distance from sensor head to water surface.
                            // Water depth (cm) = datum offset (30.0 cm) - measured distance (cm), clamped >= 0.0.
                            double depth = Math.max(0.0, 30.0 - r.getValueNum());
                            waterLevelValues.add(Math.round(depth * 100.0) / 100.0);
                        } else if (r.getSensorType() == SensorType.WATER_FLOW) {
                            continue;
                        } else if (r.getSensorType() == SensorType.BATTERY_VOLTAGE) {
                            batteryValues.add(r.getValueNum());
                        }
                    }
                }
            }
        }

        Double avgSoilMoisture = calculateAverage(soilMoistureValues);
        Double avgTemperature = calculateAverage(temperatureValues);
        Double avgHumidity = calculateAverage(humidityValues);
        Double waterLevel = calculateAverage(waterLevelValues);
        Double minBatteryVoltage = batteryValues.isEmpty() ? null : Collections.min(batteryValues);
        Integer minRssi = rssiValues.isEmpty() ? null : Collections.min(rssiValues);
        Double minSnr = snrValues.isEmpty() ? null : Collections.min(snrValues);

        ZoneHealthStatus healthStatus = evaluateZoneHealth(
                totalNodes, onlineNodes, staleNodes, offlineNodes,
                avgSoilMoisture, waterLevel, minBatteryVoltage, minRssi
        );

        ZoneTelemetryAggregate aggregate = ZoneTelemetryAggregate.builder()
                .zoneId(zoneId)
                .fieldId(fieldId != null ? fieldId : 0L)
                .avgSoilMoisture(avgSoilMoisture)
                .avgTemperature(avgTemperature)
                .avgHumidity(avgHumidity)
                .waterLevel(waterLevel)
                .minBatteryVoltage(minBatteryVoltage)
                .minRssi(minRssi)
                .minSnr(minSnr)
                .totalNodes(totalNodes)
                .onlineNodes(onlineNodes)
                .staleNodes(staleNodes)
                .offlineNodes(offlineNodes)
                .healthStatus(healthStatus)
                .calculatedAt(now)
                .build();

        aggregate = zoneTelemetryAggregateRepository.save(aggregate);
        log.info("Calculated and cached zone telemetry aggregate for zoneId: {} with status: {}", zoneId, healthStatus);

        return mapToResponse(aggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public ZoneTelemetryResponse getLatestZoneTelemetry(Long zoneId) {
        Optional<ZoneTelemetryAggregate> existing = zoneTelemetryAggregateRepository.findTopByZoneIdOrderByCalculatedAtDesc(zoneId);

        if (existing.isPresent() && existing.get().getCalculatedAt().isAfter(LocalDateTime.now().minusMinutes(CACHE_VALIDITY_MINUTES))) {
            return mapToResponse(existing.get());
        }

        return calculateAndCacheZoneTelemetry(zoneId);
    }

    @Override
    @Transactional(readOnly = true)
    public ZoneTelemetryTrendResponse getZoneTelemetryTrend(Long fieldId, Long zoneId, LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime start = startTime != null ? startTime : LocalDateTime.now().minusHours(24);
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();

        List<ZoneTelemetryAggregate> aggregates = zoneTelemetryAggregateRepository.findByZoneIdAndCalculatedAtBetweenOrderByCalculatedAtAsc(zoneId, start, end);

        if (aggregates.isEmpty()) {
            ZoneTelemetryResponse latest = calculateAndCacheZoneTelemetry(zoneId);
            ZoneTelemetryTrendPoint point = ZoneTelemetryTrendPoint.builder()
                    .timestamp(latest.getCalculatedAt())
                    .avgSoilMoisture(latest.getAvgSoilMoisture())
                    .avgTemperature(latest.getAvgTemperature())
                    .waterLevel(latest.getWaterLevel())
                    .healthStatus(latest.getHealthStatus())
                    .build();

            return ZoneTelemetryTrendResponse.builder()
                    .zoneId(zoneId)
                    .fieldId(fieldId)
                    .startTime(start)
                    .endTime(end)
                    .dataPoints(List.of(point))
                    .build();
        }

        List<ZoneTelemetryTrendPoint> trendPoints = aggregates.stream()
                .map(agg -> ZoneTelemetryTrendPoint.builder()
                        .timestamp(agg.getCalculatedAt())
                        .avgSoilMoisture(agg.getAvgSoilMoisture())
                        .avgTemperature(agg.getAvgTemperature())
                        .waterLevel(agg.getWaterLevel())
                        .healthStatus(agg.getHealthStatus())
                        .build())
                .collect(Collectors.toList());

        return ZoneTelemetryTrendResponse.builder()
                .zoneId(zoneId)
                .fieldId(fieldId)
                .startTime(start)
                .endTime(end)
                .dataPoints(trendPoints)
                .build();
    }

    private Double calculateAverage(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        double sum = 0.0;
        for (Double val : values) {
            sum += val;
        }
        return Math.round((sum / values.size()) * 100.0) / 100.0;
    }

    private ZoneHealthStatus evaluateZoneHealth(int totalNodes, int onlineNodes, int staleNodes, int offlineNodes,
                                                Double avgSoilMoisture, Double waterLevel, Double minBattery, Integer minRssi) {
        if (totalNodes == 0 || onlineNodes == 0) {
            return ZoneHealthStatus.OFFLINE;
        }

        if (offlineNodes >= onlineNodes) {
            return ZoneHealthStatus.CRITICAL;
        }

        if (avgSoilMoisture != null && avgSoilMoisture < 15.0) {
            return ZoneHealthStatus.CRITICAL;
        }

        if (minBattery != null && minBattery < 3.0) {
            return ZoneHealthStatus.CRITICAL;
        }

        if (staleNodes > 0 || (minBattery != null && minBattery < 3.4) || (minRssi != null && minRssi < -115)) {
            return ZoneHealthStatus.ATTENTION_REQUIRED;
        }

        if (avgSoilMoisture != null && avgSoilMoisture < 25.0) {
            return ZoneHealthStatus.ATTENTION_REQUIRED;
        }

        return ZoneHealthStatus.OPTIMAL;
    }

    private ZoneTelemetryResponse mapToResponse(ZoneTelemetryAggregate aggregate) {
        return ZoneTelemetryResponse.builder()
                .zoneId(aggregate.getZoneId())
                .fieldId(aggregate.getFieldId())
                .avgSoilMoisture(aggregate.getAvgSoilMoisture())
                .avgTemperature(aggregate.getAvgTemperature())
                .avgHumidity(aggregate.getAvgHumidity())
                .waterLevel(aggregate.getWaterLevel())
                .minBatteryVoltage(aggregate.getMinBatteryVoltage())
                .minRssi(aggregate.getMinRssi())
                .minSnr(aggregate.getMinSnr())
                .totalNodes(aggregate.getTotalNodes())
                .onlineNodes(aggregate.getOnlineNodes())
                .staleNodes(aggregate.getStaleNodes())
                .offlineNodes(aggregate.getOfflineNodes())
                .healthStatus(aggregate.getHealthStatus())
                .calculatedAt(aggregate.getCalculatedAt())
                .build();
    }
}
