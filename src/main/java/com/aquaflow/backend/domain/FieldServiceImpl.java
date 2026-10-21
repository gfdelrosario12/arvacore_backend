package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.request.FieldRequest;
import com.aquaflow.backend.dto.response.FieldResponse;
import com.aquaflow.backend.entity.Field;
import com.aquaflow.backend.infrastructure.exception.ResourceNotFoundException;
import com.aquaflow.backend.infrastructure.exception.ValidationException;
import com.aquaflow.backend.infrastructure.util.DtoMapper;
import com.aquaflow.backend.persistence.FieldRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class FieldServiceImpl implements FieldService {

    private static final Logger log = LoggerFactory.getLogger(FieldServiceImpl.class);

    private final FieldRepository fieldRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.aquaflow.backend.persistence.MonitoringZoneRepository monitoringZoneRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.aquaflow.backend.persistence.EdgeNodeRepository edgeNodeRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.aquaflow.backend.persistence.MonitoringPointRepository monitoringPointRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.aquaflow.backend.persistence.NodeAssignmentHistoryRepository nodeAssignmentHistoryRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.aquaflow.backend.persistence.SystemAuditLogRepository auditLogRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.aquaflow.backend.infrastructure.event.SystemEventPublisher systemEventPublisher;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AlertService alertService;

    public FieldServiceImpl(FieldRepository fieldRepository) {
        this.fieldRepository = fieldRepository;
    }

    @Override
    public FieldResponse createField(FieldRequest request) {
        log.info("Creating field: {}", request.getName());

        if (fieldRepository.existsByName(request.getName())) {
            throw new ValidationException("Field with name '" + request.getName() + "' already exists", "FIELD_DUPLICATE");
        }

        Double totalArea = request.getTotalArea() != null ? request.getTotalArea() : request.getAreaHectares();
        Double areaHectares = request.getAreaHectares() != null ? request.getAreaHectares() : totalArea;

        Field field = Field.builder()
                .name(request.getName())
                .boundaryGeoJson(request.getBoundaryGeoJson())
                .areaHectares(areaHectares)
                .lengthMeters(request.getLengthMeters())
                .widthMeters(request.getWidthMeters())
                .totalArea(totalArea)
                .areaUnit(request.getAreaUnit() != null ? request.getAreaUnit() : "HECTARES")
                .shapeType(request.getShapeType() != null ? request.getShapeType() : "RECTANGULAR")
                .operationalStatus(request.getOperationalStatus() != null ? request.getOperationalStatus() : "DRAFT")
                .description(request.getDescription())
                .locationAddress(request.getLocationAddress())
                .ownerUsername(request.getOwnerUsername())
                .build();

        Field saved = fieldRepository.save(field);
        log.info("Field created with id: {}", saved.getId());
        if (alertService != null) {
            try {
                alertService.createNotification(
                        "FIELD_CREATED",
                        "Farmland Created: " + saved.getName(),
                        String.format("Farmland '%s' (%.2f %s) was created.", saved.getName(),
                                saved.getTotalArea() != null ? saved.getTotalArea() : 0.0,
                                saved.getAreaUnit() != null ? saved.getAreaUnit() : "HECTARES"),
                        "INFO",
                        "agronomic",
                        null,
                        saved.getId(),
                        null,
                        null,
                        "OPERATOR",
                        "Farmland Management",
                        "ENTIRE FIELD",
                        "Configure monitoring zones and assign sensor nodes on the Field Dashboard.",
                        null,
                        0,
                        null
                );
            } catch (Exception ignored) {}
        }
        return DtoMapper.toFieldResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public FieldResponse getFieldById(Long id) {
        Field field = fieldRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Field not found with id: " + id));
        return DtoMapper.toFieldResponse(field);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FieldResponse> getAllFields(Pageable pageable) {
        return fieldRepository.findAll(pageable).map(DtoMapper::toFieldResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FieldResponse> getAllFields() {
        return fieldRepository.findAll().stream()
                .map(DtoMapper::toFieldResponse)
                .collect(Collectors.toList());
    }

    @Override
    public FieldResponse updateField(Long id, FieldRequest request) {
        Field field = fieldRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Field not found with id: " + id));

        if (!field.getName().equals(request.getName()) && fieldRepository.existsByName(request.getName())) {
            throw new ValidationException("Field with name '" + request.getName() + "' already exists", "FIELD_DUPLICATE");
        }

        field.setName(request.getName());
        field.setBoundaryGeoJson(request.getBoundaryGeoJson());
        if (request.getAreaHectares() != null) field.setAreaHectares(request.getAreaHectares());
        if (request.getTotalArea() != null) field.setTotalArea(request.getTotalArea());
        if (request.getLengthMeters() != null) field.setLengthMeters(request.getLengthMeters());
        if (request.getWidthMeters() != null) field.setWidthMeters(request.getWidthMeters());
        if (request.getAreaUnit() != null) field.setAreaUnit(request.getAreaUnit());
        if (request.getShapeType() != null) field.setShapeType(request.getShapeType());
        if (request.getOperationalStatus() != null) field.setOperationalStatus(request.getOperationalStatus());
        if (request.getDescription() != null) field.setDescription(request.getDescription());
        if (request.getLocationAddress() != null) field.setLocationAddress(request.getLocationAddress());
        if (request.getOwnerUsername() != null) field.setOwnerUsername(request.getOwnerUsername());

        Field saved = fieldRepository.save(field);
        log.info("Field updated with id: {}", id);
        if (alertService != null) {
            try {
                alertService.createNotification(
                        "FIELD_UPDATED",
                        "Farmland Updated: " + saved.getName(),
                        String.format("Farmland '%s' configuration and layout details were updated.", saved.getName()),
                        "INFO",
                        "agronomic",
                        null,
                        saved.getId(),
                        null,
                        null,
                        "OPERATOR",
                        "Farmland Management",
                        "ENTIRE FIELD",
                        null,
                        "FIELD_UPDATED:" + saved.getId(),
                        5,
                        null
                );
            } catch (Exception ignored) {}
        }
        return DtoMapper.toFieldResponse(saved);
    }

    @Override
    public void deleteField(Long id) {
        Field field = fieldRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Field not found with id: " + id));

        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        // 1. Reconcile all monitoring zones and assigned edge nodes in this field
        if (monitoringZoneRepository != null) {
            List<com.aquaflow.backend.entity.MonitoringZone> zones = monitoringZoneRepository.findByFieldId(id);
            for (com.aquaflow.backend.entity.MonitoringZone zone : zones) {
                if (edgeNodeRepository != null) {
                    List<com.aquaflow.backend.entity.EdgeNode> nodes = edgeNodeRepository.findByMonitoringZoneId(zone.getId());
                    for (com.aquaflow.backend.entity.EdgeNode node : nodes) {
                        node.setMonitoringZone(null);
                        edgeNodeRepository.save(node);

                        if (nodeAssignmentHistoryRepository != null) {
                            try {
                                var activeHist = nodeAssignmentHistoryRepository.findActiveAssignment(node.getId());
                                activeHist.ifPresent(h -> {
                                    h.setUnassignedAt(now);
                                    h.setReason("Field deleted: " + field.getName());
                                    nodeAssignmentHistoryRepository.save(h);
                                });
                            } catch (Exception ex) {
                                log.warn("Error reconciling assignment history for node {}: {}", node.getNodeId(), ex.getMessage());
                            }
                        }

                        if (systemEventPublisher != null) {
                            try {
                                systemEventPublisher.publishNodeStatusChanged(node.getNodeId(), null, "ACTIVE", "ACTIVE");
                            } catch (Exception ex) {
                                log.warn("Error broadcasting node detachment for node {}: {}", node.getNodeId(), ex.getMessage());
                            }
                        }
                    }
                }

                if (monitoringPointRepository != null) {
                    try {
                        List<com.aquaflow.backend.entity.MonitoringPoint> points = monitoringPointRepository.findByMonitoringZoneId(zone.getId());
                        if (points != null && !points.isEmpty()) {
                            points.forEach(p -> p.setEdgeNode(null));
                            monitoringPointRepository.deleteAll(points);
                        }
                    } catch (Exception ex) {
                        log.warn("Error cleaning monitoring points for zone {}: {}", zone.getId(), ex.getMessage());
                    }
                }

                // Delete the monitoring zone
                monitoringZoneRepository.delete(zone);
            }
        }

        // 2. Delete the field
        fieldRepository.delete(field);
        log.info("Field '{}' (id: {}) successfully deleted and dependent nodes unassigned", field.getName(), id);

        if (alertService != null) {
            try {
                alertService.createNotification(
                        "FIELD_DELETED",
                        "Farmland Removed: " + field.getName(),
                        String.format("Farmland '%s' was removed and any assigned nodes were returned to unassigned inventory.", field.getName()),
                        "WARNING",
                        "agronomic",
                        null,
                        id,
                        null,
                        null,
                        "OPERATOR",
                        "Farmland Management",
                        "ENTIRE FIELD",
                        "Reassign available nodes to an active monitoring zone.",
                        null,
                        0,
                        null
                );
            } catch (Exception ignored) {}
        }

        // 3. Audit Log
        if (auditLogRepository != null) {
            try {
                auditLogRepository.save(com.aquaflow.backend.entity.SystemAuditLog.builder()
                        .eventType("FIELD_DELETED")
                        .actor("ARVALINK_OPERATOR")
                        .entityType("Field")
                        .entityId(String.valueOf(id))
                        .previousState("ACTIVE")
                        .resultingState("DELETED")
                        .payloadJson(String.format("{\"fieldId\":%d,\"name\":\"%s\"}", id, field.getName()))
                        .createdAt(now)
                        .build());
            } catch (Exception ex) {
                log.warn("Failed to write audit log for deleted field {}: {}", id, ex.getMessage());
            }
        }
    }
}

