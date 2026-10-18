package com.aquaflow.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarmlandValidationResponse {

    private Long fieldId;
    private String fieldName;
    private String operationalStatus;
    private boolean valid;
    private boolean readyForMonitoring;

    @Builder.Default
    private List<String> blockingIssues = new ArrayList<>();

    @Builder.Default
    private List<String> advisoryIssues = new ArrayList<>();

    private Integer totalZones;
    private Double configuredArea;
    private Double expectedTotalArea;
    private Double areaCoveragePercent;
    private Integer assignedNodesCount;
    private Integer activeNodesCount;
    private Integer unassignedNodesCount;
}

