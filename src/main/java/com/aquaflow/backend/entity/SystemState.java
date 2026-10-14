package com.aquaflow.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_states")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemState {

    @Id
    @Column(name = "state_key", length = 50)
    private String stateKey;

    @Column(name = "master_enabled", nullable = false)
    private boolean masterEnabled;

    @Column(name = "updated_by", length = 100, nullable = false)
    private String updatedBy;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }
}

