package com.aquaflow.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "evaluation_reference_measurements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationReferenceMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "indicator_type", nullable = false, length = 50)
    private String indicatorType; // "WATER_LEVEL" or "SOIL_MOISTURE"

    @Column(name = "node_id", nullable = false, length = 100)
    private String nodeId;

    @Column(name = "monitoring_zone_id")
    private Long monitoringZoneId;

    @Column(name = "reported_value", nullable = false)
    private Double reportedValue;

    @Column(name = "reference_value", nullable = false)
    private Double referenceValue;

    @Column(name = "absolute_error", nullable = false)
    private Double absoluteError;

    @Column(name = "percentage_error")
    private Double percentageError;

    @Column(name = "accuracy_percentage")
    private Double accuracyPercentage;

    @Column(name = "measurement_unit", nullable = false, length = 50)
    private String measurementUnit;

    @Column(name = "instrument_details", length = 255)
    private String instrumentDetails;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "measured_by", nullable = false, length = 100)
    private String measuredBy;

    @Column(name = "measured_at", nullable = false)
    private LocalDateTime measuredAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}

