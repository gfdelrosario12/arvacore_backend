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
public class IrrigationCommandAckRequest {

    @NotBlank(message = "Controller ID is required")
    private String controllerId;

    @NotBlank(message = "Status is required")
    private String status; // RECEIVED, EXECUTED, FAILED, REJECTED

    private String action; // START_IRRIGATION, STOP_IRRIGATION, SAFE_STOP
    private String reason;
    private Double currentFlowRateLpm;
    private Double totalLiters;
    private Boolean pumpActive;
    private Boolean valveOpen;
}
