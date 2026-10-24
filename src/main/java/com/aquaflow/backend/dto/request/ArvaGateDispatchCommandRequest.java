package com.aquaflow.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArvaGateDispatchCommandRequest {
    @NotBlank
    private String targetGates; // "1", "2", "3", "1,2", "ALL"

    @NotBlank
    private String action; // OPEN, CLOSE, STOP

    private Integer durationSeconds;
    private String rationale;
}

