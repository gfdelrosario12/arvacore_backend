package com.aquaflow.backend.dto.response;

import com.aquaflow.backend.entity.PairingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PairingRequestResponse {
    private Long id;
    private String nodeId;
    private String hardwareModel;
    private String firmwareVersion;
    private String macAddress;
    private PairingStatus pairingStatus;
    private String rejectionReason;
    private LocalDateTime requestedAt;
    private LocalDateTime processedAt;
    private String processedBy;

    // Recognition details
    private boolean recognized;
    private Long existingEdgeNodeId;
    private String existingLifecycleState;
    private Long existingZoneId;
    private String existingZoneName;
}

