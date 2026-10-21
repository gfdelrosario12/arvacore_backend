package com.aquaflow.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "zone_id")
    private Long zoneId;

    @Column(name = "field_id")
    private Long fieldId;

    @Column(name = "zone_code", length = 64)
    private String zoneCode;

    @Column(name = "alert_type")
    private String alertType;

    @Column(name = "alert_level")
    private String alertLevel;

    @Column(name = "title")
    private String title;

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "actor_id", length = 128)
    private String actorId;

    @Column(name = "source_name")
    private String sourceName;

    @Column(name = "target_scope", length = 128)
    private String targetScope;

    @Column(name = "recommended_action", columnDefinition = "TEXT")
    private String recommendedAction;

    @Column(name = "dedup_key")
    private String dedupKey;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "acknowledged")
    private boolean acknowledged;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
