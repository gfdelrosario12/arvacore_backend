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
public class MasterStateUpdateRequest {
    @NotNull(message = "enabled is required")
    private Boolean enabled;
    private String notes;
}

