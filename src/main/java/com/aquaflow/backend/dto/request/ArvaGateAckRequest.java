package com.aquaflow.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArvaGateAckRequest {
    @NotBlank
    private String deviceId;

    @NotBlank
    private String status; // ACCEPTED, EXECUTED, REJECTED, FAILED

    private String details;
    private String gate1State;
    private String gate2State;
    private String gate3State;
}

