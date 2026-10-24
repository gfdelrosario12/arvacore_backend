package com.aquaflow.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "arva_gate_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArvaGateEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "command_id", length = 64)
    private String commandId;

    @Column(name = "device_id", length = 64, nullable = false)
    private String deviceId;

    @Column(name = "gate_index")
    private Integer gateIndex;

    @Column(name = "event_type", length = 64, nullable = false)
    private String eventType;

    @Column(name = "previous_state", length = 32)
    private String previousState;

    @Column(name = "new_state", length = 32)
    private String newState;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "actor", length = 64, nullable = false)
    @Builder.Default
    private String actor = "SYSTEM";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

