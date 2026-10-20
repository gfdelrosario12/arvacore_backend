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

    @Column(name = "device_key_id")
    private String deviceKeyId;

    @Column(name = "installation_id")
    private String installationId;

    @Column(name = "operational_key_id")
    private String operationalKeyId;

    @Column(name = "provisioning_token")
    private String provisioningToken;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "processed_by")
    private String processedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (requestedAt == null) {
            requestedAt = LocalDateTime.now();
        }
        if (expiresAt == null) {
            expiresAt = requestedAt.plusMinutes(30);
        }
        if (pairingStatus == null) {
            pairingStatus = PairingStatus.PENDING;
        }
        if (installationId == null) {
            installationId = "default";
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return pairingStatus == PairingStatus.EXPIRED ||
                (pairingStatus == PairingStatus.PENDING && expiresAt != null && LocalDateTime.now().isAfter(expiresAt));
    }
}
