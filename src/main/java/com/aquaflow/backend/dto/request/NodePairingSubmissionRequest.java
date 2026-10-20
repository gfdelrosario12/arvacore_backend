package com.aquaflow.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodePairingSubmissionRequest {
    @NotBlank(message = "nodeId is required")
    private String nodeId;
    private String macAddress;
    private String hardwareModel;
    private String firmwareVersion;
    private String deviceKeyId;
    private String installationId;
}
