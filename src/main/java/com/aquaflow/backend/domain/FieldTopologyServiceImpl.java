package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.response.FieldTopologyResponse;
import com.aquaflow.backend.dto.response.MonitoringZoneTopologyResponse;
import com.aquaflow.backend.entity.AutoIrrigationConfig;
import com.aquaflow.backend.entity.EdgeNode;
import com.aquaflow.backend.entity.Field;
import com.aquaflow.backend.entity.MonitoringPoint;
import com.aquaflow.backend.entity.MonitoringZone;
import com.aquaflow.backend.entity.NodeLifecycleState;
import com.aquaflow.backend.infrastructure.exception.ResourceNotFoundException;
import com.aquaflow.backend.infrastructure.exception.ValidationException;
import com.aquaflow.backend.infrastructure.util.DtoMapper;
import com.aquaflow.backend.persistence.AutoIrrigationConfigRepository;
import com.aquaflow.backend.persistence.EdgeNodeRepository;
import com.aquaflow.backend.persistence.FieldRepository;
import com.aquaflow.backend.persistence.MonitoringPointRepository;
import com.aquaflow.backend.persistence.MonitoringZoneRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class FieldTopologyServiceImpl implements FieldTopologyService {

    private static final Logger log = LoggerFactory.getLogger(FieldTopologyServiceImpl.class);

    private final FieldRepository fieldRepository;
    private final MonitoringZoneRepository monitoringZoneRepository;
    private final MonitoringPointRepository monitoringPointRepository;
    private final EdgeNodeRepository edgeNodeRepository;
    private final ZoneAggregationService zoneAggregationService;
    private final AutoIrrigationConfigRepository autoIrrigationConfigRepository;

    @Autowired
    public FieldTopologyServiceImpl(FieldRepository fieldRepository,
                                    MonitoringZoneRepository monitoringZoneRepository,
                                    MonitoringPointRepository monitoringPointRepository,
                                    EdgeNodeRepository edgeNodeRepository,
                                    @Autowired(required = false) ZoneAggregationService zoneAggregationService,
                                    @Autowired(required = false) AutoIrrigationConfigRepository autoIrrigationConfigRepository) {
        this.fieldRepository = fieldRepository;
        this.monitoringZoneRepository = monitoringZoneRepository;
        this.monitoringPointRepository = monitoringPointRepository;
        this.edgeNodeRepository = edgeNodeRepository;
        this.zoneAggregationService = zoneAggregationService;
        this.autoIrrigationConfigRepository = autoIrrigationConfigRepository;
    }

    public FieldTopologyServiceImpl(FieldRepository fieldRepository,
                                    MonitoringZoneRepository monitoringZoneRepository,
                                    MonitoringPointRepository monitoringPointRepository,
                                    EdgeNodeRepository edgeNodeRepository,
                                    ZoneAggregationService zoneAggregationService) {
        this(fieldRepository, monitoringZoneRepository, monitoringPointRepository, edgeNodeRepository, zoneAggregationService, null);
    }

    @Override
    public FieldTopologyResponse getFieldTopology(Long fieldId) {
        log.info("Fetching complete read-optimized topology for fieldId: {}", fieldId);

        Field field = fieldRepository.findById(fieldId)
                .orElseThrow(() -> new ResourceNotFoundException("Field not found with id: " + fieldId));

        List<MonitoringZone> zones = monitoringZoneRepository.findByFieldId(fieldId).stream()
                .filter(z -> !Boolean.TRUE.equals(z.getIsArchived()))
                .toList();
        List<MonitoringZoneTopologyResponse> zoneTopologies = new ArrayList<>();

        for (MonitoringZone zone : zones) {
            List<MonitoringPoint> points = monitoringPointRepository.findByMonitoringZoneId(zone.getId());
            List<EdgeNode> nodes = edgeNodeRepository.findByMonitoringZoneId(zone.getId()).stream()
                    .filter(n -> n.getLifecycleState() != NodeLifecycleState.UNREGISTERED &&
                                 n.getLifecycleState() != NodeLifecycleState.DECOMMISSIONED)
                    .toList();

            MonitoringZoneTopologyResponse zoneTopology = DtoMapper.toMonitoringZoneTopologyResponse(zone, points, nodes);
            if (zoneAggregationService != null) {
                try {
                    zoneTopology.setTelemetrySummary(zoneAggregationService.calculateAndCacheZoneTelemetry(zone.getId()));
                } catch (Exception e) {
                    log.warn("Failed to fetch zone telemetry summary for zoneId {}: {}", zone.getId(), e.getMessage());
                }
            }
            zoneTopologies.add(zoneTopology);
        }

        FieldTopologyResponse response = DtoMapper.toFieldTopologyResponse(field, zoneTopologies);

        if (autoIrrigationConfigRepository != null) {
            Optional<AutoIrrigationConfig> configOpt = autoIrrigationConfigRepository.findByFieldId(fieldId);
            configOpt.ifPresent(config -> response.setActiveConfigVersion(config.getConfigVersion()));
        }

        return response;
    }

    @Override
    public void validateNodeAssignmentToZone(Long edgeNodeId, Long monitoringZoneId) {
        if (edgeNodeId == null || monitoringZoneId == null) {
            return;
        }

        EdgeNode node = edgeNodeRepository.findById(edgeNodeId)
                .orElseThrow(() -> new ResourceNotFoundException("EdgeNode not found with id: " + edgeNodeId));

        MonitoringZone zone = monitoringZoneRepository.findById(monitoringZoneId)
                .orElseThrow(() -> new ResourceNotFoundException("MonitoringZone not found with id: " + monitoringZoneId));

        if (node.getMonitoringZone() != null && !node.getMonitoringZone().getId().equals(zone.getId())) {
            throw new ValidationException(
                    "Edge node " + edgeNodeId + " is already assigned to a different monitoring zone " + node.getMonitoringZone().getId(),
                    "INVALID_TOPOLOGY_ASSIGNMENT"
            );
        }
    }

    @Override
    public void validatePointAssignmentToNode(Long monitoringPointId, Long edgeNodeId) {
        if (monitoringPointId == null || edgeNodeId == null) {
            return;
        }

        MonitoringPoint point = monitoringPointRepository.findById(monitoringPointId)
                .orElseThrow(() -> new ResourceNotFoundException("MonitoringPoint not found with id: " + monitoringPointId));

        EdgeNode node = edgeNodeRepository.findById(edgeNodeId)
                .orElseThrow(() -> new ResourceNotFoundException("EdgeNode not found with id: " + edgeNodeId));

        if (point.getMonitoringZone() != null && node.getMonitoringZone() != null) {
            if (!point.getMonitoringZone().getId().equals(node.getMonitoringZone().getId())) {
                throw new ValidationException(
                        "MonitoringPoint zone " + point.getMonitoringZone().getId() + " does not match EdgeNode zone " + node.getMonitoringZone().getId(),
                        "INVALID_TOPOLOGY_ASSIGNMENT"
                );
            }
        }
    }
}
