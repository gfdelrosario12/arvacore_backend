package com.aquaflow.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "arva_gate_commands")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArvaGateCommand {

    @Id
    @Column(name = "command_id", length = 64)
    private String commandId;

    @Column(name = "device_id", length = 64, nullable = false)
    private String deviceId;

    @Column(name = "target_gates", length = 64, nullable = false)
    private String targetGates; // "1", "2", "3", "1,2", "ALL"

    @Column(name = "action", length = 32, nullable = false)
    private String action; // OPEN, CLOSE, STOP

    @Column(name = "duration_seconds")
    @Builder.Default
    private Integer durationSeconds = 0;

    @Column(name = "issued_by", length = 64, nullable = false)
    private String issuedBy;

    @Column(name = "status", length = 32, nullable = false)
    @Builder.Default
    private String status = "PENDING"; // PENDING, ACCEPTED, EXECUTED, REJECTED, FAILED

    @Column(name = "error_details", columnDefinition = "TEXT")
    private String errorDetails;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}

