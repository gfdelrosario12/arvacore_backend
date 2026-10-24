package com.aquaflow.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "arva_gates", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"device_id", "gate_index"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArvaGate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", length = 64, nullable = false)
    private String deviceId;

    @Column(name = "gate_index", nullable = false)
    private Integer gateIndex; // 1, 2, or 3

    @Column(name = "gate_label", length = 64, nullable = false)
    private String gateLabel;

    @Column(name = "current_state", length = 32, nullable = false)
    @Builder.Default
    private String currentState = "CLOSED"; // OPEN, CLOSED, OPENING, CLOSING, STOPPED, FAULT

    @Column(name = "requested_state", length = 32, nullable = false)
    @Builder.Default
    private String requestedState = "CLOSED";

    @Column(name = "physical_confirmed", nullable = false)
    @Builder.Default
    private Boolean physicalConfirmed = false;

    @Column(name = "last_actuated_at")
    private LocalDateTime lastActuatedAt;

    @Column(name = "fault_message", columnDefinition = "TEXT")
    private String faultMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

