package com.aquaflow.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoreHealthResponse {
    private String status; // UP, DEGRADED, DOWN
    private boolean masterEnabled;
    private boolean databaseConnected;
    private boolean telemetryIngestionActive;
    private int activeWebSocketSessions;
    private int recognizedNodesCount;
    private int onlineNodesCount;
    private int pendingPairingRequestsCount;
    private LocalDateTime timestamp;
    private Map<String, Object> details;
}

