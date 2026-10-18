package com.aquaflow.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodePairingResetResponse {
    private int totalRequested;
    private int succeededCount;
    private int failedCount;
    private List<String> resetNodeIds;
    private List<String> failedNodeIds;
    private String message;
    private LocalDateTime timestamp;
}

