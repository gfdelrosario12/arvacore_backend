package com.aquaflow.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MasterStateResponse {
    private boolean masterEnabled;
    private String updatedBy;
    private String notes;
    private LocalDateTime updatedAt;
}

