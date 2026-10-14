package com.aquaflow.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "node_pairing_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodePairingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "node_id", nullable = false)
    private String nodeId;

    @Column(name = "hardware_model")
    private String hardwareModel;

    @Column(name = "firmware_version")
    private String firmwareVersion;

    @Column(name = "mac_address")
    private String macAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "pairing_status", nullable = false)
    private PairingStatus pairingStatus;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "processed_by")
    private String processedBy;

    @PrePersist
    protected void onCreate() {
        if (requestedAt == null) {
            requestedAt = LocalDateTime.now();
        }
        if (pairingStatus == null) {
            pairingStatus = PairingStatus.PENDING;
        }
    }
}

