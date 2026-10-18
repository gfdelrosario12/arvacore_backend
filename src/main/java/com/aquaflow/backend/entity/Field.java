package com.aquaflow.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "fields")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Field {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "boundary_geojson", columnDefinition = "TEXT")
    private String boundaryGeoJson;

    @Column(name = "area_hectares")
    private Double areaHectares;

    @Column(name = "length_meters")
    private Double lengthMeters;

    @Column(name = "width_meters")
    private Double widthMeters;

    @Column(name = "total_area")
    private Double totalArea;

    @Column(name = "area_unit", length = 50)
    private String areaUnit;

    @Column(name = "shape_type", length = 50)
    private String shapeType;

    @Column(name = "operational_status", length = 50)
    private String operationalStatus;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "location_address", length = 255)
    private String locationAddress;

    @Column(name = "owner_username", length = 100)
    private String ownerUsername;

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

