package com.aquaflow.backend.persistence;

import com.aquaflow.backend.entity.EvaluationReferenceMeasurement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvaluationReferenceMeasurementRepository extends JpaRepository<EvaluationReferenceMeasurement, Long> {
    List<EvaluationReferenceMeasurement> findByIndicatorTypeOrderByMeasuredAtDesc(String indicatorType);
    List<EvaluationReferenceMeasurement> findByIndicatorTypeAndNodeIdOrderByMeasuredAtDesc(String indicatorType, String nodeId);
    Page<EvaluationReferenceMeasurement> findByIndicatorType(String indicatorType, Pageable pageable);
}

