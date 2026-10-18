package com.aquaflow.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "monitoring_zones")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_id", nullable = false)
    private Field field;

    @Column(name = "crop_type")
    private String cropType;

    @Column(name = "target_moisture_percentage")
    private Double targetMoisturePercentage;

    @Column(name = "water_allocation_limit_liters")
    private Double waterAllocationLimitLiters;

    @Column(name = "code", length = 50)
    private String code;

    @Column(name = "area_type", length = 50)
    private String areaType;

    @Column(name = "length_meters")
    private Double lengthMeters;

    @Column(name = "width_meters")
    private Double widthMeters;

    @Column(name = "area_size")
    private Double areaSize;

    @Column(name = "area_unit", length = 50)
    private String areaUnit;

    @Column(name = "relative_x")
    private Double relativeX;

    @Column(name = "relative_y")
    private Double relativeY;

    @Column(name = "relative_width")
    private Double relativeWidth;

    @Column(name = "relative_height")
    private Double relativeHeight;

    @Column(name = "boundary_geojson", columnDefinition = "TEXT")
    private String boundaryGeoJson;

    @Column(name = "operational_status", length = 50)
    private String operationalStatus;

    @Column(name = "is_archived", nullable = false)
    @Builder.Default
    private Boolean isArchived = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

