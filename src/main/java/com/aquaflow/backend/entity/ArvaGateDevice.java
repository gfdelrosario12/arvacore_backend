package com.aquaflow.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "arva_gate_devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArvaGateDevice {

    @Id
    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "display_name", length = 128, nullable = false)
    private String displayName;

    @Column(name = "field_id", nullable = false)
    @Builder.Default
    private Long fieldId = 1L;

    @Column(name = "firmware_version", length = 32, nullable = false)
    @Builder.Default
    private String firmwareVersion = "1.0.0";

    @Column(name = "connectivity_status", length = 32, nullable = false)
    @Builder.Default
    private String connectivityStatus = "ONLINE";

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

