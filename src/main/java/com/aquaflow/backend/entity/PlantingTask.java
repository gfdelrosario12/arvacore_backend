package com.aquaflow.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "planting_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlantingTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    @JsonIgnore
    private PlantingPlan plantingPlan;

    @Column(name = "category", nullable = false, length = 50)
    private String category; // SYSTEM_AUTOMATED, FARMER_MANUAL, EQUIPMENT_INFO, SAFETY_BLOCKED

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false, length = 50)
    private String status; // PENDING, IN_PROGRESS, COMPLETED, BLOCKED

    @Column(name = "blocking_reason", length = 255)
    private String blockingReason;

    @Column(name = "requires_confirmation", nullable = false)
    private Boolean requiresConfirmation;

    @Column(name = "executed_at")
    private OffsetDateTime executedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

