package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.request.UpdateAwdProfileRequest;
import com.aquaflow.backend.dto.request.UpdateCropStageRequest;
import com.aquaflow.backend.dto.request.ZoneRequest;
import com.aquaflow.backend.dto.response.MonitoringZoneResponse;
import com.aquaflow.backend.dto.response.ZoneResponse;
import com.aquaflow.backend.entity.EdgeNode;
import com.aquaflow.backend.entity.MonitoringZone;
import com.aquaflow.backend.entity.NodeLifecycleState;
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
    private final com.aquaflow.backend.persistence.FieldRepository fieldRepository;
    private final com.aquaflow.backend.persistence.NodeAssignmentHistoryRepository nodeAssignmentHistoryRepository;
    private final com.aquaflow.backend.persistence.MonitoringPointRepository monitoringPointRepository;
    @org.springframework.context.annotation.Lazy
    private final FarmlandValidationService farmlandValidationService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AlertService alertService;

    @org.springframework.beans.factory.annotation.Autowired
    public ZoneServiceImpl(ZoneRepository zoneRepository,
                           MonitoringZoneRepository monitoringZoneRepository,
                           @org.springframework.beans.factory.annotation.Autowired(required = false) EdgeNodeRepository edgeNodeRepository,
                           @org.springframework.beans.factory.annotation.Autowired(required = false) ZoneAggregationService zoneAggregationService,
                           @org.springframework.beans.factory.annotation.Autowired(required = false) com.aquaflow.backend.persistence.FieldRepository fieldRepository,
                           @org.springframework.beans.factory.annotation.Autowired(required = false) com.aquaflow.backend.persistence.NodeAssignmentHistoryRepository nodeAssignmentHistoryRepository,
                           @org.springframework.beans.factory.annotation.Autowired(required = false) com.aquaflow.backend.persistence.MonitoringPointRepository monitoringPointRepository,
                           @org.springframework.beans.factory.annotation.Autowired(required = false) @org.springframework.context.annotation.Lazy FarmlandValidationService farmlandValidationService) {
        this.zoneRepository = zoneRepository;
        this.monitoringZoneRepository = monitoringZoneRepository;
        this.edgeNodeRepository = edgeNodeRepository;
        this.zoneAggregationService = zoneAggregationService;
        this.fieldRepository = fieldRepository;
        this.nodeAssignmentHistoryRepository = nodeAssignmentHistoryRepository;
        this.monitoringPointRepository = monitoringPointRepository;
        this.farmlandValidationService = farmlandValidationService;
    }

    public ZoneServiceImpl(ZoneRepository zoneRepository, MonitoringZoneRepository monitoringZoneRepository) {
        this(zoneRepository, monitoringZoneRepository, null, null, null, null, null, null);
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

        // Also ensure a corresponding MonitoringZone exists for the primary field so it appears in field topologies and dashboards
        MonitoringZone mz = null;
        if (fieldRepository != null && monitoringZoneRepository != null) {
            try {
                com.aquaflow.backend.entity.Field field = fieldRepository.findAll().stream().findFirst().orElse(null);
                if (field != null) {
                    mz = MonitoringZone.builder()
                            .name(saved.getName())
                            .code("Q" + saved.getId())
                            .field(field)
                            .cropType(saved.getCropType())
                            .areaSize(saved.getArea())
                            .areaUnit("HECTARES")
                            .areaType("CUSTOM")
                            .waterAllocationLimitLiters(saved.getWaterAllocationLimit() != null ? saved.getWaterAllocationLimit() : 1000.0)
                            .targetMoisturePercentage(70.0)
                            .operationalStatus("READY")
                            .isArchived(false)
                            .build();
                    monitoringZoneRepository.save(mz);
                }
            } catch (Exception e) {
                log.warn("Could not create complementary monitoring zone for {}: {}", saved.getName(), e.getMessage());
            }
        }

        if (alertService != null) {
            try {
                alertService.createNotification(
                        "ZONE_CREATED",
                        "Monitoring Zone Created: " + saved.getName(),
                        String.format("Monitoring zone '%s' (%s) was created.", saved.getName(), saved.getCropType()),
                        "INFO",
                        "agronomic",
                        null,
                        null,
                        saved.getId(),
                        "Q" + saved.getId(),
                        "OPERATOR",
                        "Monitoring Quadrant Q" + saved.getId(),
                        "Q" + saved.getId(),
                        null,
                        null,
                        0,
                        null
                );
            } catch (Exception ignored) {}
        }
        return mz != null ? toAwdZoneResponse(mz) : DtoMapper.toZoneResponse(saved);
    }

    @Override
    public ZoneResponse getZoneById(Long id) {
        java.util.Optional<MonitoringZone> mzOpt = monitoringZoneRepository.findById(id);
        if (mzOpt.isPresent()) {
            return toAwdZoneResponse(mzOpt.get());
        }

        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found with id: " + id));
        return DtoMapper.toZoneResponse(zone);
    }

    @Override
    @Transactional(readOnly = true)
    public MonitoringZoneResponse getMonitoringZoneById(Long id) {
        MonitoringZone zone = monitoringZoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Monitoring zone not found with id: " + id));
        return enrichZoneResponse(zone);
    }

    @Override
    public Page<ZoneResponse> getAllZones(Pageable pageable) {
        List<MonitoringZone> activeMz = monitoringZoneRepository.findByIsArchivedFalse();
        if (activeMz.isEmpty()) {
            activeMz = monitoringZoneRepository.findAll().stream()
                    .filter(z -> !Boolean.TRUE.equals(z.getIsArchived()))
                    .collect(Collectors.toList());
        }
        if (!activeMz.isEmpty()) {
            List<ZoneResponse> awdZones = activeMz.stream()
                    .map(this::toAwdZoneResponse)
                    .collect(Collectors.toList());
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), awdZones.size());
            if (start > awdZones.size()) {
                return new org.springframework.data.domain.PageImpl<>(Collections.emptyList(), pageable, awdZones.size());
            }
            return new org.springframework.data.domain.PageImpl<>(awdZones.subList(start, end), pageable, awdZones.size());
        }
        if (zoneRepository.count() > 0) {
            return zoneRepository.findAll(pageable).map(DtoMapper::toZoneResponse);
        }
        return new org.springframework.data.domain.PageImpl<>(Collections.emptyList(), pageable, 0);
    }

    @Override
    public List<ZoneResponse> getAllZones() {
        List<MonitoringZone> activeMz = monitoringZoneRepository.findByIsArchivedFalse();
        if (activeMz.isEmpty()) {
            activeMz = monitoringZoneRepository.findAll().stream()
                    .filter(z -> !Boolean.TRUE.equals(z.getIsArchived()))
                    .collect(Collectors.toList());
        }
        if (!activeMz.isEmpty()) {
            return activeMz.stream()
                    .map(this::toAwdZoneResponse)
                    .collect(Collectors.toList());
        }
        if (zoneRepository.count() > 0) {
            return zoneRepository.findAll().stream()
                    .map(DtoMapper::toZoneResponse)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private ZoneResponse toAwdZoneResponse(MonitoringZone mz) {
        List<EdgeNode> allNodes = edgeNodeRepository != null
                ? edgeNodeRepository.findByMonitoringZoneId(mz.getId())
                : Collections.emptyList();
        List<EdgeNode> nodes = allNodes.stream()
                .filter(n -> n.getLifecycleState() != NodeLifecycleState.UNREGISTERED &&
                             n.getLifecycleState() != NodeLifecycleState.DECOMMISSIONED)
                .toList();
        List<String> nodeIds = new ArrayList<>();
        Integer battery = null;
        for (EdgeNode n : nodes) {
            if (n.getNodeId() != null && !n.getNodeId().isBlank()) {
                nodeIds.add(n.getNodeId());
            }
            if (battery == null && n.getHealthMetrics() != null && n.getHealthMetrics().getBatteryLevel() != null) {
                battery = n.getHealthMetrics().getBatteryLevel().intValue();
            }
        }

        Double avgMoisture = null;
        Double waterLevel = null;
        String status = nodes.isEmpty() ? "OFFLINE" : "OPTIMAL";
        Boolean isOnline = false;

        if (zoneAggregationService != null && !nodes.isEmpty()) {
            try {
                com.aquaflow.backend.dto.response.ZoneTelemetryResponse telemetry =
                        zoneAggregationService.calculateAndCacheZoneTelemetry(mz.getId());
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
        String code = (mz.getCode() != null && !mz.getCode().isBlank()) ? mz.getCode() : "Q" + mz.getId();

        return ZoneResponse.builder()
                .id(mz.getId())
                .name(name)
                .code(code)
                .fieldId(mz.getField() != null ? mz.getField().getId() : null)
                .cropType(mz.getCropType())
                .waterAllocationLimit(mz.getWaterAllocationLimitLiters())
                .soilMoisturePercent(avgMoisture != null ? avgMoisture : 0.0)
                .waterLevelCm(waterLevel != null ? waterLevel : 0.0)
                .assignedNodeIds(nodeIds)
                .status(status)
                .batteryPercent(battery != null ? battery : 0)
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
        boolean deletedAny = false;
        if (monitoringZoneRepository != null && monitoringZoneRepository.existsById(id)) {
            archiveMonitoringZone(id);
            deletedAny = true;
            log.info("MonitoringZone archived with id: {}", id);
        }
        if (zoneRepository.existsById(id)) {
            zoneRepository.deleteById(id);
            deletedAny = true;
            log.info("Zone deleted with id: {}", id);
        }
        if (!deletedAny) {
            throw new ResourceNotFoundException("Zone not found with id: " + id);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ZoneResponse> getZonesByCropType(String cropType) {
        return zoneRepository.findByCropType(cropType).stream()
                .map(DtoMapper::toZoneResponse)
                .collect(Collectors.toList());
    }

    private MonitoringZoneResponse enrichZoneResponse(MonitoringZone zone) {
        MonitoringZoneResponse res = DtoMapper.toMonitoringZoneResponse(zone);
        if (edgeNodeRepository != null) {
            List<EdgeNode> nodes = edgeNodeRepository.findByMonitoringZoneId(zone.getId());
            List<EdgeNode> activeNodes = nodes.stream()
                    .filter(n -> n.getLifecycleState() != NodeLifecycleState.UNREGISTERED &&
                                 n.getLifecycleState() != NodeLifecycleState.DECOMMISSIONED)
                    .toList();
            res.setAssignedNodeCount(activeNodes.size());
            res.setAssignedNodeIds(activeNodes.stream().map(EdgeNode::getNodeId).collect(Collectors.toList()));
        } else {
            res.setAssignedNodeCount(0);
            res.setAssignedNodeIds(Collections.emptyList());
        }
        return res;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MonitoringZoneResponse> getMonitoringZonesByFieldId(Long fieldId) {
        return monitoringZoneRepository.findByFieldIdAndIsArchivedFalse(fieldId).stream()
                .map(this::enrichZoneResponse)
                .collect(Collectors.toList());
    }

    @Override
    public MonitoringZoneResponse createMonitoringZone(Long fieldId, com.aquaflow.backend.dto.request.MonitoringZoneRequest request) {
        com.aquaflow.backend.entity.Field field = fieldRepository.findById(fieldId)
                .orElseThrow(() -> new ResourceNotFoundException("Field not found with id: " + fieldId));

        MonitoringZone zone = MonitoringZone.builder()
                .name(request.getName())
                .code(request.getCode() != null ? request.getCode() : request.getName().toUpperCase().replaceAll("\\s+", "-"))
                .field(field)
                .cropType(request.getCropType() != null ? request.getCropType() : "Rice - Inbred NSIC Rc222")
                .targetMoisturePercentage(request.getTargetMoisturePercentage() != null ? request.getTargetMoisturePercentage() : 70.0)
                .waterAllocationLimitLiters(request.getWaterAllocationLimitLiters() != null ? request.getWaterAllocationLimitLiters() : 1000.0)
                .areaType(request.getAreaType() != null ? request.getAreaType() : "CUSTOM")
                .lengthMeters(request.getLengthMeters())
                .widthMeters(request.getWidthMeters())
                .areaSize(request.getAreaSize())
                .areaUnit(request.getAreaUnit() != null ? request.getAreaUnit() : "HECTARES")
                .relativeX(request.getRelativeX() != null ? request.getRelativeX() : 0.0)
                .relativeY(request.getRelativeY() != null ? request.getRelativeY() : 0.0)
                .relativeWidth(request.getRelativeWidth() != null ? request.getRelativeWidth() : 1.0)
                .relativeHeight(request.getRelativeHeight() != null ? request.getRelativeHeight() : 1.0)
                .boundaryGeoJson(request.getBoundaryGeoJson())
                .operationalStatus(request.getOperationalStatus() != null ? request.getOperationalStatus() : "READY")
                .isArchived(false)
                .build();

        MonitoringZone saved = monitoringZoneRepository.save(zone);
        if (farmlandValidationService != null) {
            try {
                farmlandValidationService.updateFarmlandOperationalStatus(fieldId);
            } catch (Exception ex) {
                log.warn("Could not update operational status for field {}: {}", fieldId, ex.getMessage());
            }
        }
        if (alertService != null) {
            try {
                alertService.createNotification(
                        "MONITORING_ZONE_CREATED",
                        "Monitoring Zone Created: " + saved.getName(),
                        String.format("Monitoring zone '%s' (%s) was added to farmland '%s'.",
                                saved.getName(), saved.getCode(), field.getName()),
                        "INFO",
                        "agronomic",
                        null,
                        fieldId,
                        saved.getId(),
                        saved.getCode(),
                        "OPERATOR",
                        "Monitoring Quadrant " + saved.getCode(),
                        saved.getCode(),
                        "Assign an active ESP32 sensor node to begin monitoring this zone.",
                        null,
                        0,
                        null
                );
            } catch (Exception ignored) {}
        }
        return enrichZoneResponse(saved);
    }

    @Override
    public MonitoringZoneResponse updateMonitoringZone(Long zoneId, com.aquaflow.backend.dto.request.MonitoringZoneRequest request) {
        MonitoringZone zone = monitoringZoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Monitoring zone not found with id: " + zoneId));

        if (request.getName() != null && !request.getName().isBlank()) zone.setName(request.getName());
        if (request.getCode() != null && !request.getCode().isBlank()) zone.setCode(request.getCode());
        if (request.getAreaType() != null) zone.setAreaType(request.getAreaType());
        if (request.getCropType() != null) zone.setCropType(request.getCropType());
        if (request.getTargetMoisturePercentage() != null) zone.setTargetMoisturePercentage(request.getTargetMoisturePercentage());
        if (request.getWaterAllocationLimitLiters() != null) zone.setWaterAllocationLimitLiters(request.getWaterAllocationLimitLiters());
        if (request.getLengthMeters() != null) zone.setLengthMeters(request.getLengthMeters());
        if (request.getWidthMeters() != null) zone.setWidthMeters(request.getWidthMeters());
        if (request.getAreaSize() != null) zone.setAreaSize(request.getAreaSize());
        if (request.getAreaUnit() != null) zone.setAreaUnit(request.getAreaUnit());
        if (request.getRelativeX() != null) zone.setRelativeX(request.getRelativeX());
        if (request.getRelativeY() != null) zone.setRelativeY(request.getRelativeY());
        if (request.getRelativeWidth() != null) zone.setRelativeWidth(request.getRelativeWidth());
        if (request.getRelativeHeight() != null) zone.setRelativeHeight(request.getRelativeHeight());
        if (request.getBoundaryGeoJson() != null) zone.setBoundaryGeoJson(request.getBoundaryGeoJson());
        if (request.getOperationalStatus() != null) zone.setOperationalStatus(request.getOperationalStatus());

        MonitoringZone saved = monitoringZoneRepository.save(zone);
        if (farmlandValidationService != null && saved.getField() != null) {
            try {
                farmlandValidationService.updateFarmlandOperationalStatus(saved.getField().getId());
            } catch (Exception ex) {
                log.warn("Could not update operational status for field {}: {}", saved.getField().getId(), ex.getMessage());
            }
        }
        if (alertService != null) {
            try {
                alertService.createNotification(
                        "MONITORING_ZONE_UPDATED",
                        "Monitoring Zone Updated: " + saved.getName(),
                        String.format("Monitoring zone '%s' (%s) configuration was updated.", saved.getName(), saved.getCode()),
                        "INFO",
                        "agronomic",
                        null,
                        saved.getField() != null ? saved.getField().getId() : null,
                        saved.getId(),
                        saved.getCode(),
                        "OPERATOR",
                        "Monitoring Quadrant " + saved.getCode(),
                        saved.getCode(),
                        null,
                        "ZONE_UPDATED:" + saved.getId(),
                        5,
                        null
                );
            } catch (Exception ignored) {}
        }
        return enrichZoneResponse(saved);
    }

    @Override
    public void archiveMonitoringZone(Long zoneId) {
        MonitoringZone zone = monitoringZoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Monitoring zone not found with id: " + zoneId));

        // Unassign any nodes
        if (edgeNodeRepository != null) {
            List<EdgeNode> nodes = edgeNodeRepository.findByMonitoringZoneId(zoneId);
            for (EdgeNode n : nodes) {
                n.setMonitoringZone(null);
                edgeNodeRepository.save(n);
                if (nodeAssignmentHistoryRepository != null) {
                    var act = nodeAssignmentHistoryRepository.findActiveAssignment(n.getId());
                    act.ifPresent(h -> {
                        h.setUnassignedAt(java.time.LocalDateTime.now());
                        nodeAssignmentHistoryRepository.save(h);
                    });
                }
            }
        }

        zone.setIsArchived(true);
        zone.setOperationalStatus("ARCHIVED");
        monitoringZoneRepository.save(zone);

        if (alertService != null) {
            try {
                alertService.createNotification(
                        "MONITORING_ZONE_REMOVED",
                        "Monitoring Zone Removed: " + zone.getName(),
                        String.format("Monitoring zone '%s' (%s) was archived and any assigned nodes were unassigned.",
                                zone.getName(), zone.getCode()),
                        "WARNING",
                        "agronomic",
                        null,
                        zone.getField() != null ? zone.getField().getId() : null,
                        zone.getId(),
                        zone.getCode(),
                        "OPERATOR",
                        "Monitoring Quadrant " + zone.getCode(),
                        zone.getCode(),
                        "Reassign unassigned nodes to an active monitoring zone.",
                        null,
                        0,
                        null
                );
            } catch (Exception ignored) {}
        }

        if (farmlandValidationService != null && zone.getField() != null) {
            try {
                farmlandValidationService.updateFarmlandOperationalStatus(zone.getField().getId());
            } catch (Exception ex) {
                log.warn("Could not update operational status for field {}: {}", zone.getField().getId(), ex.getMessage());
            }
        }
    }

    @Override
    public List<MonitoringZoneResponse> splitMonitoringZone(Long zoneId, com.aquaflow.backend.dto.request.SplitZoneRequest request) {
        MonitoringZone zone = monitoringZoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Monitoring zone not found with id: " + zoneId));

        if (Boolean.TRUE.equals(zone.getIsArchived())) {
            throw new ValidationException("Cannot split an already archived zone");
        }

        double ratio = (request.getRatio() != null && request.getRatio() > 0.05 && request.getRatio() < 0.95)
                ? request.getRatio() : 0.5;
        String orientation = request.getOrientation() != null ? request.getOrientation().toUpperCase() : "VERTICAL";

        double origX = zone.getRelativeX() != null ? zone.getRelativeX() : 0.0;
        double origY = zone.getRelativeY() != null ? zone.getRelativeY() : 0.0;
        double origW = zone.getRelativeWidth() != null ? zone.getRelativeWidth() : 1.0;
        double origH = zone.getRelativeHeight() != null ? zone.getRelativeHeight() : 1.0;

        double origLen = zone.getLengthMeters() != null ? zone.getLengthMeters() : 50.0;
        double origWid = zone.getWidthMeters() != null ? zone.getWidthMeters() : 50.0;
        double origArea = zone.getAreaSize() != null ? zone.getAreaSize() : 0.25;

        // Part 1
        double p1X = origX, p1Y = origY, p1W = origW, p1H = origH;
        double p1Len = origLen, p1Wid = origWid;
        // Part 2
        double p2X = origX, p2Y = origY, p2W = origW, p2H = origH;
        double p2Len = origLen, p2Wid = origWid;

        if ("HORIZONTAL".equals(orientation)) {
            p1H = origH * ratio;
            p1Wid = origWid * ratio;
            p2Y = origY + p1H;
            p2H = origH * (1.0 - ratio);
            p2Wid = origWid * (1.0 - ratio);
        } else {
            p1W = origW * ratio;
            p1Len = origLen * ratio;
            p2X = origX + p1W;
            p2W = origW * (1.0 - ratio);
            p2Len = origLen * (1.0 - ratio);
        }

        String codePrefix = zone.getCode() != null ? zone.getCode() : "Z";
        String p1Name = request.getPart1Name() != null ? request.getPart1Name() : zone.getName() + " (North/West)";
        String p1Code = request.getPart1Code() != null ? request.getPart1Code() : codePrefix + "-A";
        String p2Name = request.getPart2Name() != null ? request.getPart2Name() : zone.getName() + " (South/East)";
        String p2Code = request.getPart2Code() != null ? request.getPart2Code() : codePrefix + "-B";

        MonitoringZone part1 = MonitoringZone.builder()
                .name(p1Name)
                .code(p1Code)
                .field(zone.getField())
                .areaType(zone.getAreaType())
                .cropType(zone.getCropType())
                .targetMoisturePercentage(zone.getTargetMoisturePercentage())
                .waterAllocationLimitLiters(zone.getWaterAllocationLimitLiters() != null ? zone.getWaterAllocationLimitLiters() * ratio : null)
                .relativeX(p1X)
                .relativeY(p1Y)
                .relativeWidth(p1W)
                .relativeHeight(p1H)
                .lengthMeters(p1Len)
                .widthMeters(p1Wid)
                .areaSize(origArea * ratio)
                .areaUnit(zone.getAreaUnit())
                .operationalStatus("READY")
                .isArchived(false)
                .build();

        MonitoringZone part2 = MonitoringZone.builder()
                .name(p2Name)
                .code(p2Code)
                .field(zone.getField())
                .areaType(zone.getAreaType())
                .cropType(zone.getCropType())
                .targetMoisturePercentage(zone.getTargetMoisturePercentage())
                .waterAllocationLimitLiters(zone.getWaterAllocationLimitLiters() != null ? zone.getWaterAllocationLimitLiters() * (1.0 - ratio) : null)
                .relativeX(p2X)
                .relativeY(p2Y)
                .relativeWidth(p2W)
                .relativeHeight(p2H)
                .lengthMeters(p2Len)
                .widthMeters(p2Wid)
                .areaSize(origArea * (1.0 - ratio))
                .areaUnit(zone.getAreaUnit())
                .operationalStatus("READY")
                .isArchived(false)
                .build();

        MonitoringZone savedPart1 = monitoringZoneRepository.save(part1);
        MonitoringZone savedPart2 = monitoringZoneRepository.save(part2);

        // Reassign nodes from original zone to part 1
        if (edgeNodeRepository != null) {
            List<EdgeNode> existingNodes = edgeNodeRepository.findByMonitoringZoneId(zoneId);
            for (EdgeNode n : existingNodes) {
                n.setMonitoringZone(savedPart1);
                edgeNodeRepository.save(n);
                if (nodeAssignmentHistoryRepository != null) {
                    var act = nodeAssignmentHistoryRepository.findActiveAssignment(n.getId());
                    act.ifPresent(h -> {
                        h.setUnassignedAt(java.time.LocalDateTime.now());
                        nodeAssignmentHistoryRepository.save(h);
                    });
                    com.aquaflow.backend.entity.NodeAssignmentHistory newH = com.aquaflow.backend.entity.NodeAssignmentHistory.builder()
                            .edgeNodeId(n.getId())
                            .nodeId(n.getNodeId())
                            .fieldId(zone.getField() != null ? zone.getField().getId() : null)
                            .monitoringZoneId(savedPart1.getId())
                            .assignedAt(java.time.LocalDateTime.now())
                            .assignedBy("SYSTEM_SPLIT")
                            .reason("Zone split automatic reassignment")
                            .build();
                    nodeAssignmentHistoryRepository.save(newH);
                }
            }
        }

        // Archive original zone
        zone.setIsArchived(true);
        zone.setOperationalStatus("ARCHIVED");
        monitoringZoneRepository.save(zone);

        if (alertService != null) {
            try {
                alertService.createNotification(
                        "MONITORING_ZONE_REMOVED",
                        "Monitoring Zone Removed: " + zone.getName(),
                        String.format("Monitoring zone '%s' (%s) was archived and any assigned nodes were unassigned.",
                                zone.getName(), zone.getCode()),
                        "WARNING",
                        "agronomic",
                        null,
                        zone.getField() != null ? zone.getField().getId() : null,
                        zone.getId(),
                        zone.getCode(),
                        "OPERATOR",
                        "Monitoring Quadrant " + zone.getCode(),
                        zone.getCode(),
                        "Reassign unassigned nodes to an active monitoring zone.",
                        null,
                        0,
                        null
                );
            } catch (Exception ignored) {}
        }

        if (farmlandValidationService != null && zone.getField() != null) {
            try {
                farmlandValidationService.updateFarmlandOperationalStatus(zone.getField().getId());
            } catch (Exception ex) {
                log.warn("Could not update operational status for field {}: {}", zone.getField().getId(), ex.getMessage());
            }
        }

        List<MonitoringZoneResponse> results = new ArrayList<>();
        results.add(enrichZoneResponse(savedPart1));
        results.add(enrichZoneResponse(savedPart2));
        return results;
    }

    @Override
    public MonitoringZoneResponse mergeMonitoringZones(Long fieldId, com.aquaflow.backend.dto.request.MergeZonesRequest request) {
        if (request.getZoneIds() == null || request.getZoneIds().size() < 2) {
            throw new ValidationException("At least two zone IDs are required to perform a merge");
        }

        com.aquaflow.backend.entity.Field field = fieldRepository.findById(fieldId)
                .orElseThrow(() -> new ResourceNotFoundException("Field not found with id: " + fieldId));

        List<MonitoringZone> zonesToMerge = monitoringZoneRepository.findAllById(request.getZoneIds());
        if (zonesToMerge.size() < 2) {
            throw new ValidationException("Could not find all designated zones for merging");
        }

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;
        double totalArea = 0.0;
        double totalWater = 0.0;

        for (MonitoringZone z : zonesToMerge) {
            if (Boolean.TRUE.equals(z.getIsArchived())) {
                throw new ValidationException("Zone " + z.getName() + " is already archived");
            }
            double zX = z.getRelativeX() != null ? z.getRelativeX() : 0.0;
            double zY = z.getRelativeY() != null ? z.getRelativeY() : 0.0;
            double zW = z.getRelativeWidth() != null ? z.getRelativeWidth() : 1.0;
            double zH = z.getRelativeHeight() != null ? z.getRelativeHeight() : 1.0;

            if (zX < minX) minX = zX;
            if (zY < minY) minY = zY;
            if ((zX + zW) > maxX) maxX = (zX + zW);
            if ((zY + zH) > maxY) maxY = (zY + zH);

            if (z.getAreaSize() != null) totalArea += z.getAreaSize();
            if (z.getWaterAllocationLimitLiters() != null) totalWater += z.getWaterAllocationLimitLiters();
        }

        String mergedName = request.getMergedName() != null ? request.getMergedName() : "Merged Section (" + zonesToMerge.size() + " zones)";
        String mergedCode = request.getMergedCode() != null ? request.getMergedCode() : "M-" + (System.currentTimeMillis() % 10000);

        MonitoringZone merged = MonitoringZone.builder()
                .name(mergedName)
                .code(mergedCode)
                .field(field)
                .areaType(request.getAreaType() != null ? request.getAreaType() : "SECTION")
                .cropType(request.getCropType() != null ? request.getCropType() : zonesToMerge.get(0).getCropType())
                .targetMoisturePercentage(zonesToMerge.get(0).getTargetMoisturePercentage())
                .waterAllocationLimitLiters(totalWater > 0 ? totalWater : null)
                .relativeX(minX != Double.MAX_VALUE ? minX : 0.0)
                .relativeY(minY != Double.MAX_VALUE ? minY : 0.0)
                .relativeWidth(maxX > minX ? (maxX - minX) : 1.0)
                .relativeHeight(maxY > minY ? (maxY - minY) : 1.0)
                .areaSize(totalArea > 0 ? totalArea : null)
                .areaUnit(zonesToMerge.get(0).getAreaUnit())
                .operationalStatus("READY")
                .isArchived(false)
                .build();

        MonitoringZone savedMerged = monitoringZoneRepository.save(merged);

        // Reassign all nodes from old zones to merged zone
        if (edgeNodeRepository != null) {
            for (MonitoringZone z : zonesToMerge) {
                List<EdgeNode> nodes = edgeNodeRepository.findByMonitoringZoneId(z.getId());
                for (EdgeNode n : nodes) {
                    n.setMonitoringZone(savedMerged);
                    edgeNodeRepository.save(n);
                    if (nodeAssignmentHistoryRepository != null) {
                        var act = nodeAssignmentHistoryRepository.findActiveAssignment(n.getId());
                        act.ifPresent(h -> {
                            h.setUnassignedAt(java.time.LocalDateTime.now());
                            nodeAssignmentHistoryRepository.save(h);
                        });
                        com.aquaflow.backend.entity.NodeAssignmentHistory newH = com.aquaflow.backend.entity.NodeAssignmentHistory.builder()
                                .edgeNodeId(n.getId())
                                .nodeId(n.getNodeId())
                                .fieldId(field.getId())
                                .monitoringZoneId(savedMerged.getId())
                                .assignedAt(java.time.LocalDateTime.now())
                                .assignedBy("SYSTEM_MERGE")
                                .reason("Zone merge automatic reassignment")
                                .build();
                        nodeAssignmentHistoryRepository.save(newH);
                    }
                }
            }
        }

        // Archive constituent zones
        for (MonitoringZone z : zonesToMerge) {
            z.setIsArchived(true);
            z.setOperationalStatus("ARCHIVED");
            monitoringZoneRepository.save(z);
        }

        if (farmlandValidationService != null) {
            try {
                farmlandValidationService.updateFarmlandOperationalStatus(fieldId);
            } catch (Exception ex) {
                log.warn("Could not update operational status for field {}: {}", fieldId, ex.getMessage());
            }
        }

        return enrichZoneResponse(savedMerged);
    }

    @Override
    public com.aquaflow.backend.dto.response.FieldResponse updateFarmlandLayout(Long fieldId, com.aquaflow.backend.dto.request.FarmlandLayoutDto layout) {
        com.aquaflow.backend.entity.Field field = fieldRepository.findById(fieldId)
                .orElseThrow(() -> new ResourceNotFoundException("Field not found with id: " + fieldId));

        if (layout.getName() != null && !layout.getName().isBlank()) field.setName(layout.getName());
        if (layout.getLengthMeters() != null) field.setLengthMeters(layout.getLengthMeters());
        if (layout.getWidthMeters() != null) field.setWidthMeters(layout.getWidthMeters());
        if (layout.getTotalArea() != null) {
            field.setTotalArea(layout.getTotalArea());
            field.setAreaHectares(layout.getTotalArea());
        }
        if (layout.getAreaUnit() != null) field.setAreaUnit(layout.getAreaUnit());
        if (layout.getShapeType() != null) field.setShapeType(layout.getShapeType());
        if (layout.getDescription() != null) field.setDescription(layout.getDescription());
        if (layout.getLocationAddress() != null) field.setLocationAddress(layout.getLocationAddress());

        com.aquaflow.backend.entity.Field savedField = fieldRepository.save(field);

        if (layout.getZones() != null && !layout.getZones().isEmpty()) {
            for (com.aquaflow.backend.dto.request.MonitoringZoneRequest zReq : layout.getZones()) {
                if (zReq.getCode() != null) {
                    var existingOpt = monitoringZoneRepository.findByFieldIdAndCode(fieldId, zReq.getCode());
                    if (existingOpt.isPresent()) {
                        MonitoringZone z = existingOpt.get();
                        if (zReq.getName() != null) z.setName(zReq.getName());
                        if (zReq.getAreaType() != null) z.setAreaType(zReq.getAreaType());
                        if (zReq.getRelativeX() != null) z.setRelativeX(zReq.getRelativeX());
                        if (zReq.getRelativeY() != null) z.setRelativeY(zReq.getRelativeY());
                        if (zReq.getRelativeWidth() != null) z.setRelativeWidth(zReq.getRelativeWidth());
                        if (zReq.getRelativeHeight() != null) z.setRelativeHeight(zReq.getRelativeHeight());
                        if (zReq.getAreaSize() != null) z.setAreaSize(zReq.getAreaSize());
                        if (zReq.getLengthMeters() != null) z.setLengthMeters(zReq.getLengthMeters());
                        if (zReq.getWidthMeters() != null) z.setWidthMeters(zReq.getWidthMeters());
                        if (zReq.getCropType() != null) z.setCropType(zReq.getCropType());
                        monitoringZoneRepository.save(z);
                        continue;
                    }
                }
                // Create as new zone
                createMonitoringZone(fieldId, zReq);
            }
        }

        if (farmlandValidationService != null) {
            try {
                farmlandValidationService.updateFarmlandOperationalStatus(fieldId);
            } catch (Exception ex) {
                log.warn("Could not update operational status for field {}: {}", fieldId, ex.getMessage());
            }
        }

        return DtoMapper.toFieldResponse(fieldRepository.findById(fieldId).get());
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.aquaflow.backend.dto.response.NodeAssignmentHistoryResponse> getFieldAssignmentHistory(Long fieldId) {
        if (nodeAssignmentHistoryRepository == null) return Collections.emptyList();
        com.aquaflow.backend.entity.Field field = fieldRepository.findById(fieldId).orElse(null);
        String fieldName = field != null ? field.getName() : null;

        List<com.aquaflow.backend.entity.NodeAssignmentHistory> list =
                nodeAssignmentHistoryRepository.findByFieldIdOrderByAssignedAtDesc(fieldId);

        List<com.aquaflow.backend.dto.response.NodeAssignmentHistoryResponse> responses = new ArrayList<>();
        for (com.aquaflow.backend.entity.NodeAssignmentHistory h : list) {
            String zoneName = null;
            String zoneCode = null;
            if (h.getMonitoringZoneId() != null) {
                var z = monitoringZoneRepository.findById(h.getMonitoringZoneId());
                if (z.isPresent()) {
                    zoneName = z.get().getName();
                    zoneCode = z.get().getCode();
                }
            }
            responses.add(DtoMapper.toNodeAssignmentHistoryResponse(h, fieldName, zoneName, zoneCode));
        }
        return responses;
    }
}