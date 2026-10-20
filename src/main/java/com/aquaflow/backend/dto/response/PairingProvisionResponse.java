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
public class PairingProvisionResponse {
    private Long pairingRequestId;
    private String nodeId;
    private String operationalKeyId;
    private String operationalSecret;
    private PairingStatus status;
    private String action;
    private LocalDateTime expiresAt;
}

