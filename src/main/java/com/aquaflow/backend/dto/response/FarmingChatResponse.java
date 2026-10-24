package com.aquaflow.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarmingChatResponse {
    private String sessionId;
    private String currentStep;
    private String replyText;
    private Long planId;
    private String planCode;
    private Integer readinessScore;
    private String readinessStatus;
    private String promptQuestion;
    private Boolean requiresConfirmation;
    private List<PlantingTaskDto> tasks;
    private List<String> agronomicReferences;
    private List<String> warnings;
    private String disclaimer;

    // RBAC Context dynamically communicated to the frontend client
    private String userRole;
    private Boolean canControlIrrigation;
    private Boolean canManageUsers;
    private Boolean canViewAuditLogs;
    private List<String> authorizedCapabilities;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlantingTaskDto {
        private Long id;
        private String category;
        private String title;
        private String description;
        private String status;
        private String blockingReason;
        private Boolean requiresConfirmation;
    }
}
