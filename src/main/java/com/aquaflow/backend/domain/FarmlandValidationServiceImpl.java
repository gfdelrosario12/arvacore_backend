package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.response.FarmlandValidationResponse;
import com.aquaflow.backend.entity.EdgeNode;
import com.aquaflow.backend.entity.Field;
import com.aquaflow.backend.entity.HealthState;
import com.aquaflow.backend.entity.MonitoringZone;
import com.aquaflow.backend.entity.NodeLifecycleState;
import com.aquaflow.backend.infrastructure.exception.ResourceNotFoundException;
import com.aquaflow.backend.persistence.EdgeNodeRepository;
import com.aquaflow.backend.persistence.FieldRepository;
import com.aquaflow.backend.persistence.MonitoringZoneRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class FarmlandValidationServiceImpl implements FarmlandValidationService {

    private static final Logger log = LoggerFactory.getLogger(FarmlandValidationServiceImpl.class);

    private final FieldRepository fieldRepository;
    private final MonitoringZoneRepository monitoringZoneRepository;
    private final EdgeNodeRepository edgeNodeRepository;

    public FarmlandValidationServiceImpl(FieldRepository fieldRepository,
                                         MonitoringZoneRepository monitoringZoneRepository,
                                         EdgeNodeRepository edgeNodeRepository) {
        this.fieldRepository = fieldRepository;
        this.monitoringZoneRepository = monitoringZoneRepository;
        this.edgeNodeRepository = edgeNodeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public FarmlandValidationResponse validateFarmlandReadiness(Long fieldId) {
        Field field = fieldRepository.findById(fieldId)
                .orElseThrow(() -> new ResourceNotFoundException("Field not found with id: " + fieldId));

        List<MonitoringZone> zones = monitoringZoneRepository.findByFieldIdAndIsArchivedFalse(fieldId);
        List<EdgeNode> allAssignedNodes = new ArrayList<>();
        for (MonitoringZone zone : zones) {
            allAssignedNodes.addAll(edgeNodeRepository.findByMonitoringZoneId(zone.getId()).stream()
                    .filter(n -> n.getLifecycleState() != NodeLifecycleState.UNREGISTERED &&
                                 n.getLifecycleState() != NodeLifecycleState.DECOMMISSIONED)
                    .toList());
        }

        List<EdgeNode> allNodesInSystem = edgeNodeRepository.findAll();
        long unassignedCount = allNodesInSystem.stream()
                .filter(n -> n.getMonitoringZone() == null &&
                             n.getLifecycleState() != NodeLifecycleState.UNREGISTERED &&
                             n.getLifecycleState() != NodeLifecycleState.DECOMMISSIONED)
                .count();

        List<String> blocking = new ArrayList<>();
        List<String> advisory = new ArrayList<>();

        // 1. Field Dimension and Area Validations
        if (field.getTotalArea() == null || field.getTotalArea() <= 0.0) {
            if (field.getAreaHectares() == null || field.getAreaHectares() <= 0.0) {
                blocking.add("Field total area must be a positive value.");
            }
        }

        if (field.getLengthMeters() != null && field.getLengthMeters() <= 0.0) {
            blocking.add("Field length in meters must be positive.");
        }
        if (field.getWidthMeters() != null && field.getWidthMeters() <= 0.0) {
            blocking.add("Field width in meters must be positive.");
        }

        // 2. Monitoring Zones Validations
        if (zones.isEmpty()) {
            blocking.add("No monitoring zones or quarters configured for this field.");
        }

        double configuredArea = 0.0;
        int activeNodesCount = 0;
        boolean hasDegradedNode = false;

        for (MonitoringZone zone : zones) {
            if (zone.getName() == null || zone.getName().isBlank()) {
                blocking.add("Zone ID " + zone.getId() + " is missing a designated name.");
            }

            if (zone.getAreaSize() != null) {
                configuredArea += zone.getAreaSize();
            } else if (zone.getLengthMeters() != null && zone.getWidthMeters() != null) {
                configuredArea += (zone.getLengthMeters() * zone.getWidthMeters());
            }

            List<EdgeNode> zoneNodes = edgeNodeRepository.findByMonitoringZoneId(zone.getId());
            if (zoneNodes.isEmpty()) {
                advisory.add("Monitoring zone '" + zone.getName() + " (" + (zone.getCode() != null ? zone.getCode() : "N/A") + ")' has no ArvaNode assigned.");
            } else {
                for (EdgeNode node : zoneNodes) {
                    if (node.getLifecycleState() == NodeLifecycleState.DECOMMISSIONED ||
                        node.getLifecycleState() == NodeLifecycleState.REPLACED ||
                        node.getLifecycleState() == NodeLifecycleState.UNREGISTERED) {
                        blocking.add("Assigned node " + node.getNodeId() + " in zone '" + zone.getName() + "' is in inactive state: " + node.getLifecycleState());
                    } else {
                        activeNodesCount++;
                    }

                    if (node.getHealthState() == HealthState.CRITICAL || node.getHealthState() == HealthState.DEGRADED) {
                        hasDegradedNode = true;
                        advisory.add("Node " + node.getNodeId() + " in zone '" + zone.getName() + "' reports " + node.getHealthState() + " health.");
                    }
                }
            }
        }

        Double expectedTotalArea = field.getTotalArea() != null ? field.getTotalArea() : field.getAreaHectares();
        Double coveragePercent = (expectedTotalArea != null && expectedTotalArea > 0.0 && configuredArea > 0.0)
                ? (configuredArea / expectedTotalArea) * 100.0 : 100.0;

        if (expectedTotalArea != null && configuredArea > (expectedTotalArea * 1.05)) {
            advisory.add(String.format("Configured area (%.2f) exceeds farm total area (%.2f) by more than 5%%.", configuredArea, expectedTotalArea));
        }

        // Determine Operational Status
        String opStatus;
        boolean isValid = blocking.isEmpty();
        boolean isReadyForMonitoring = isValid && activeNodesCount > 0;

        if (!isValid) {
            opStatus = zones.isEmpty() ? "DRAFT" : "INCOMPLETE";
        } else if (activeNodesCount == 0) {
            opStatus = "READY";
        } else if (hasDegradedNode) {
            opStatus = "DEGRADED";
        } else {
            opStatus = "MONITORING";
        }

        return FarmlandValidationResponse.builder()
                .fieldId(field.getId())
                .fieldName(field.getName())
                .operationalStatus(opStatus)
                .valid(isValid)
                .readyForMonitoring(isReadyForMonitoring)
                .blockingIssues(blocking)
                .advisoryIssues(advisory)
                .totalZones(zones.size())
                .configuredArea(configuredArea)
                .expectedTotalArea(expectedTotalArea)
                .areaCoveragePercent(coveragePercent)
                .assignedNodesCount(allAssignedNodes.size())
                .activeNodesCount(activeNodesCount)
                .unassignedNodesCount((int) unassignedCount)
                .build();
    }

    @Override
    public void updateFarmlandOperationalStatus(Long fieldId) {
        FarmlandValidationResponse validation = validateFarmlandReadiness(fieldId);
        Field field = fieldRepository.findById(fieldId).orElse(null);
        if (field != null && !validation.getOperationalStatus().equals(field.getOperationalStatus())) {
            field.setOperationalStatus(validation.getOperationalStatus());
            fieldRepository.save(field);
            log.info("Updated field {} operational status to {}", field.getId(), validation.getOperationalStatus());
        }
    }
}

