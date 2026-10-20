package com.aquaflow.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "device_credentials")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "node_id", nullable = false, unique = true)
    private String nodeId;

    @Column(name = "bootstrap_key_id", nullable = false)
    private String bootstrapKeyId;

    @Column(name = "bootstrap_secret", nullable = false)
    private String bootstrapSecret;

    @Column(name = "operational_key_id")
    private String operationalKeyId;

    @Column(name = "operational_secret")
    private String operationalSecret;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_state", nullable = false)
    private DeviceCredentialState credentialState;

    @Column(name = "installation_id", nullable = false)
    private String installationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (credentialState == null) {
            credentialState = DeviceCredentialState.BOOTSTRAP;
        }
        if (installationId == null) {
            installationId = "default";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
