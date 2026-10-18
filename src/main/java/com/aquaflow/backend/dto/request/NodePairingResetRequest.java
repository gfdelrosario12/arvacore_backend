package com.aquaflow.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodePairingResetRequest {
    private List<String> nodeIds;
    private String reason;
    @Builder.Default
    private boolean clearFieldAssignments = true;
}

