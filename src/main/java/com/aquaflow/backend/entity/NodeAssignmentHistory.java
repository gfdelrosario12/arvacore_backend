package com.aquaflow.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "node_assignment_histories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeAssignmentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "edge_node_id", nullable = false)
    private Long edgeNodeId;

    @Column(name = "node_id", nullable = false)
    private String nodeId;

    @Column(name = "field_id")
    private Long fieldId;

    @Column(name = "monitoring_zone_id")
    private Long monitoringZoneId;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @Column(name = "unassigned_at")
    private LocalDateTime unassignedAt;

    @Column(name = "assigned_by", length = 100)
    private String assignedBy;

    @Column(name = "reason", length = 255)
    private String reason;
}

