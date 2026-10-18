package com.aquaflow.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeAssignmentHistoryResponse {

    private Long id;
    private Long edgeNodeId;
    private String nodeId;
    private Long fieldId;
    private String fieldName;
    private Long monitoringZoneId;
    private String monitoringZoneName;
    private String monitoringZoneCode;
    private LocalDateTime assignedAt;
    private LocalDateTime unassignedAt;
    private String assignedBy;
    private String reason;
}

