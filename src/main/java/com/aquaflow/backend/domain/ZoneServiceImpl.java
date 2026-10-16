package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.request.UpdateAwdProfileRequest;
import com.aquaflow.backend.dto.request.UpdateCropStageRequest;
import com.aquaflow.backend.dto.request.ZoneRequest;
import com.aquaflow.backend.dto.response.MonitoringZoneResponse;
import com.aquaflow.backend.dto.response.ZoneResponse;
import com.aquaflow.backend.entity.EdgeNode;
import com.aquaflow.backend.entity.MonitoringZone;
import com.aquaflow.backend.entity.Zone;
import com.aquaflow.backend.infrastructure.exception.ResourceNotFoundException;
import com.aquaflow.backend.infrastructure.exception.ValidationException;
import com.aquaflow.backend.infrastructure.util.DtoMapper;
import com.aquaflow.backend.persistence.EdgeNodeRepository;
import com.aquaflow.backend.persistence.MonitoringZoneRepository;
import com.aquaflow.backend.persistence.ZoneRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ZoneServiceImpl implements ZoneService {

    private static final Logger log = LoggerFactory.getLogger(ZoneServiceImpl.class);

    private final ZoneRepository zoneRepository;
    private final MonitoringZoneRepository monitoringZoneRepository;
    private final EdgeNodeRepository edgeNodeRepository;
    private final ZoneAggregationService zoneAggregationService;

    @org.springframework.beans.factory.annotation.Autowired
    public ZoneServiceImpl(ZoneRepository zoneRepository,
                           MonitoringZoneRepository monitoringZoneRepository,
                           @org.springframework.beans.factory.annotation.Autowired(required = false) EdgeNodeRepository edgeNodeRepository,
                           @org.springframework.beans.factory.annotation.Autowired(required = false) ZoneAggregationService zoneAggregationService) {
        this.zoneRepository = zoneRepository;
        this.monitoringZoneRepository = monitoringZoneRepository;
        this.edgeNodeRepository = edgeNodeRepository;
        this.zoneAggregationService = zoneAggregationService;
    }

    public ZoneServiceImpl(ZoneRepository zoneRepository, MonitoringZoneRepository monitoringZoneRepository) {
        this(zoneRepository, monitoringZoneRepository, null, null);
    }

    @Override
    public ZoneResponse createZone(ZoneRequest request) {
        log.info("Creating zone: {}", request.getName());

        if (zoneRepository.existsByName(request.getName())) {
            throw new ValidationException("Zone with name '" + request.getName() + "' already exists", "ZONE_DUPLICATE");
        }

        Zone zone = Zone.builder()
                .name(request.getName())
                .area(request.getArea())
                .cropType(request.getCropType())
                .waterAllocationLimit(request.getWaterAllocationLimit())
                .build();

        Zone saved = zoneRepository.save(zone);
        log.info("Zone created with id: {}", saved.getId());
        return DtoMapper.toZoneResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ZoneResponse getZoneById(Long id) {
        java.util.Optional<Zone> zoneOpt = zoneRepository.findById(id);
        if (zoneOpt.isPresent()) {
            return DtoMapper.toZoneResponse(zoneOpt.get());
        }

        MonitoringZone mz = monitoringZoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found with id: " + id));
        return toAwdZoneResponse(mz);
    }

    @Override
    @Transactional(readOnly = true)
    public MonitoringZoneResponse getMonitoringZoneById(Long id) {
        MonitoringZone zone = monitoringZoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Monitoring zone not found with id: " + id));
        return DtoMapper.toMonitoringZoneResponse(zone);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ZoneResponse> getAllZones(Pageable pageable) {
        if (zoneRepository.count() > 0) {
            return zoneRepository.findAll(pageable).map(DtoMapper::toZoneResponse);
        }
        List<ZoneResponse> awdZones = monitoringZoneRepository.findAll().stream()
                .map(this::toAwdZoneResponse)
                .collect(Collectors.toList());
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), awdZones.size());
        if (start > awdZones.size()) {
            return new org.springframework.data.domain.PageImpl<>(Collections.emptyList(), pageable, awdZones.size());
        }
        return new org.springframework.data.domain.PageImpl<>(awdZones.subList(start, end), pageable, awdZones.size());
    }

    @Override
    public List<ZoneResponse> getAllZones() {
        if (zoneRepository.count() > 0) {
            return zoneRepository.findAll().stream()
                    .map(DtoMapper::toZoneResponse)
                    .collect(Collectors.toList());
        }
        return monitoringZoneRepository.findAll().stream()
                .map(this::toAwdZoneResponse)
                .collect(Collectors.toList());
    }

    private ZoneResponse toAwdZoneResponse(MonitoringZone mz) {
        List<EdgeNode> nodes = edgeNodeRepository != null
                ? edgeNodeRepository.findByMonitoringZoneId(mz.getId())
                : Collections.emptyList();
        List<String> nodeIds = new ArrayList<>();
        Integer battery = null;
        for (EdgeNode n : nodes) {
            if (n.getNodeId() != null) nodeIds.add(n.getNodeId());
            nodeIds.add(String.valueOf(n.getId()));
            if (battery == null && n.getHealthMetrics() != null && n.getHealthMetrics().getBatteryLevel() != null) {
                battery = n.getHealthMetrics().getBatteryLevel().intValue();
            }
        }

        Double avgMoisture = null;
        Double waterLevel = null;
        String status = "OPTIMAL";
        Boolean isOnline = !nodes.isEmpty();

        if (zoneAggregationService != null) {
            try {
                com.aquaflow.backend.dto.response.ZoneTelemetryResponse telemetry =
                        zoneAggregationService.getLatestZoneTelemetry(mz.getId());
                if (telemetry != null) {
                    avgMoisture = telemetry.getAvgSoilMoisture();
                    waterLevel = telemetry.getWaterLevel();
                    if (telemetry.getHealthStatus() != null) {
                        status = telemetry.getHealthStatus().name();
                    }
                    if (telemetry.getOnlineNodes() != null) {
                        isOnline = telemetry.getOnlineNodes() > 0;
                    }
                }
            } catch (Exception ignored) {}
        }

        String name = mz.getName() != null ? mz.getName() : "Zone " + mz.getId();
        String code = "Q" + mz.getId();
        if (name.contains("Q")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("Q[0-9]+").matcher(name);
            if (m.find()) code = m.group();
        }

        return ZoneResponse.builder()
                .id(mz.getId())
                .name(name)
                .code(code)
                .fieldId(mz.getField() != null ? mz.getField().getId() : null)
                .cropType(mz.getCropType())
                .waterAllocationLimit(mz.getWaterAllocationLimitLiters())
                .soilMoisturePercent(avgMoisture != null ? avgMoisture : mz.getTargetMoisturePercentage())
                .waterLevelCm(waterLevel != null ? waterLevel : 0.0)
                .assignedNodeIds(nodeIds)
                .status(status)
                .batteryPercent(battery != null ? battery : 95)
                .isOnline(isOnline)
                .createdAt(mz.getCreatedAt())
                .updatedAt(mz.getUpdatedAt())
                .build();
    }

    @Override
    public ZoneResponse updateZone(Long id, ZoneRequest request) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found with id: " + id));

        zone.setName(request.getName());
        zone.setArea(request.getArea());
        zone.setCropType(request.getCropType());
        zone.setWaterAllocationLimit(request.getWaterAllocationLimit());

        Zone saved = zoneRepository.save(zone);
        log.info("Zone updated with id: {}", id);
        return DtoMapper.toZoneResponse(saved);
    }

    @Override
    public MonitoringZoneResponse updateCropStage(Long id, UpdateCropStageRequest request) {
        log.info("Updating crop stage for zone {}: {}", id, request.getGrowthStage());

        MonitoringZone zone = monitoringZoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Monitoring zone not found with id: " + id));

        zone.setCropType(request.getGrowthStage());

        MonitoringZone saved = monitoringZoneRepository.save(zone);
        return DtoMapper.toMonitoringZoneResponse(saved);
    }

    @Override
    public MonitoringZoneResponse updateAwdProfile(Long id, UpdateAwdProfileRequest request) {
        log.info("Updating AWD profile for zone {}: moisture target {}%", id, request.getTargetMoisturePercentage());

        MonitoringZone zone = monitoringZoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Monitoring zone not found with id: " + id));

        zone.setTargetMoisturePercentage(request.getTargetMoisturePercentage());

        MonitoringZone saved = monitoringZoneRepository.save(zone);
        return DtoMapper.toMonitoringZoneResponse(saved);
    }

    @Override
    public void deleteZone(Long id) {
        if (!zoneRepository.existsById(id)) {
            throw new ResourceNotFoundException("Zone not found with id: " + id);
        }
        zoneRepository.deleteById(id);
        log.info("Zone deleted with id: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ZoneResponse> getZonesByCropType(String cropType) {
        return zoneRepository.findByCropType(cropType).stream()
                .map(DtoMapper::toZoneResponse)
                .collect(Collectors.toList());
    }
}