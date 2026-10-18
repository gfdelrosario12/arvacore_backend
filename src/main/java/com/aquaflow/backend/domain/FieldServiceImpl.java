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
        return DtoMapper.toFieldResponse(saved);
    }

    @Override
    public void deleteField(Long id) {
        if (!fieldRepository.existsById(id)) {
            throw new ResourceNotFoundException("Field not found with id: " + id);
        }
        fieldRepository.deleteById(id);
        log.info("Field deleted with id: {}", id);
    }
}

