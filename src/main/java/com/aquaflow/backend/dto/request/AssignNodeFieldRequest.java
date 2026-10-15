package com.aquaflow.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignNodeFieldRequest {

    private Long fieldId;

    @NotNull(message = "Monitoring zone ID is required")
    private Long monitoringZoneId;
}
