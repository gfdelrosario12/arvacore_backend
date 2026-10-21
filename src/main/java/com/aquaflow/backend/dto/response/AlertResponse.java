package com.aquaflow.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertResponse {

    private Long id;
    private String deviceId;
    private String nodeId;
    private Long zoneId;
    private Long fieldId;
    private String zoneCode;
    private String alertType;
    private String eventType;
    private String alertLevel;
    private String severity;
    private String category;
    private String title;
    private String message;
    private String description;
    private String source;
    private String targetScope;
    private String actorId;
    private String recommendedAction;
    private boolean acknowledged;

    @JsonProperty("isRead")
    private boolean isRead;

    private LocalDateTime createdAt;
    private LocalDateTime timestamp;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime readAt;
    private String metadataJson;

    @JsonProperty("isRead")
    public boolean getIsRead() {
        return isRead;
    }
}
