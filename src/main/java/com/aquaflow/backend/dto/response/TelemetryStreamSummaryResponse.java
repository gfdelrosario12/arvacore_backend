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
public class TelemetryStreamSummaryResponse {

    private long totalTransmissions;
    private long recentTransmissionsCount;
    private long distinctNodesCount;
    private long failedOrRejectedCount;
    private LocalDateTime lastEventReceivedAt;
    private int activeConnectionsCount;
}

