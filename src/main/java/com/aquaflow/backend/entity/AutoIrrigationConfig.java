package com.aquaflow.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "auto_irrigation_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoIrrigationConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_id")
    @ToString.Exclude
    private Field field;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "edge_node_id", unique = true)
    @ToString.Exclude
    private EdgeNode edgeNode;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "max_duration_minutes")
    private Integer maxDurationMinutes;

    @Column(name = "min_cooldown_minutes")
    private Integer minCooldownMinutes;

    @Column(name = "allowed_start_hour")
    private Integer allowedStartHour;

    @Column(name = "allowed_end_hour")
    private Integer allowedEndHour;

    @Column(name = "target_flood_depth_cm")
    private Double targetFloodDepthCm;

    @Column(name = "rain_delay_hours")
    private Integer rainDelayHours;

    @Column(name = "min_confidence_threshold")
    private Double minConfidenceThreshold;

    @Column(name = "config_version", nullable = false)
    @Builder.Default
    private Long configVersion = 1L;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "change_reason")
    private String changeReason;

    @OneToMany(mappedBy = "autoIrrigationConfig", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    private List<AwdThresholdConfig> thresholds = new ArrayList<>();

    // Legacy fields retained for backwards compatibility
    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_mode")
    @Builder.Default
    private ScheduleMode scheduleMode = ScheduleMode.AUTOMATIC;

    @Column(name = "min_soil_moisture_percentage")
    private Double minSoilMoisturePercentage;

    @Column(name = "max_soil_moisture_percentage")
    private Double maxSoilMoisturePercentage;

    @Column(name = "max_single_run_minutes")
    private Integer maxSingleRunMinutes;

    @Column(name = "safety_rain_override")
    private Boolean safetyRainOverride;

    @Column(name = "cron_schedule")
    private String cronSchedule;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Field getField() { return field; }
    public void setField(Field field) { this.field = field; }
    public EdgeNode getEdgeNode() { return edgeNode; }
    public void setEdgeNode(EdgeNode edgeNode) { this.edgeNode = edgeNode; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Integer getMaxDurationMinutes() { return maxDurationMinutes; }
    public void setMaxDurationMinutes(Integer maxDurationMinutes) { this.maxDurationMinutes = maxDurationMinutes; }
    public Integer getMinCooldownMinutes() { return minCooldownMinutes; }
    public void setMinCooldownMinutes(Integer minCooldownMinutes) { this.minCooldownMinutes = minCooldownMinutes; }
    public Integer getAllowedStartHour() { return allowedStartHour; }
    public void setAllowedStartHour(Integer allowedStartHour) { this.allowedStartHour = allowedStartHour; }
    public Integer getAllowedEndHour() { return allowedEndHour; }
    public void setAllowedEndHour(Integer allowedEndHour) { this.allowedEndHour = allowedEndHour; }
    public Double getTargetFloodDepthCm() { return targetFloodDepthCm; }
    public void setTargetFloodDepthCm(Double targetFloodDepthCm) { this.targetFloodDepthCm = targetFloodDepthCm; }
    public Integer getRainDelayHours() { return rainDelayHours; }
    public void setRainDelayHours(Integer rainDelayHours) { this.rainDelayHours = rainDelayHours; }
    public Double getMinConfidenceThreshold() { return minConfidenceThreshold; }
    public void setMinConfidenceThreshold(Double minConfidenceThreshold) { this.minConfidenceThreshold = minConfidenceThreshold; }
    public Long getConfigVersion() { return configVersion; }
    public void setConfigVersion(Long configVersion) { this.configVersion = configVersion; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
    public List<AwdThresholdConfig> getThresholds() { return thresholds; }
    public void setThresholds(List<AwdThresholdConfig> thresholds) { this.thresholds = thresholds; }
    public ScheduleMode getScheduleMode() { return scheduleMode; }
    public void setScheduleMode(ScheduleMode scheduleMode) { this.scheduleMode = scheduleMode; }
    public Double getMinSoilMoisturePercentage() { return minSoilMoisturePercentage; }
    public void setMinSoilMoisturePercentage(Double minSoilMoisturePercentage) { this.minSoilMoisturePercentage = minSoilMoisturePercentage; }
    public Double getMaxSoilMoisturePercentage() { return maxSoilMoisturePercentage; }
    public void setMaxSoilMoisturePercentage(Double maxSoilMoisturePercentage) { this.maxSoilMoisturePercentage = maxSoilMoisturePercentage; }
    public Integer getMaxSingleRunMinutes() { return maxSingleRunMinutes; }
    public void setMaxSingleRunMinutes(Integer maxSingleRunMinutes) { this.maxSingleRunMinutes = maxSingleRunMinutes; }
    public Boolean getSafetyRainOverride() { return safetyRainOverride; }
    public void setSafetyRainOverride(Boolean safetyRainOverride) { this.safetyRainOverride = safetyRainOverride; }
    public String getCronSchedule() { return cronSchedule; }
    public void setCronSchedule(String cronSchedule) { this.cronSchedule = cronSchedule; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (configVersion == null) {
            configVersion = 1L;
        }
        if (enabled == null) {
            enabled = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

