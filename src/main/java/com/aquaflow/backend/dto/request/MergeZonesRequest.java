package com.aquaflow.backend.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MergeZonesRequest {

    @NotEmpty(message = "At least two zone IDs are required for merging")
    private List<Long> zoneIds;

    private String mergedName;
    private String mergedCode;
    private String cropType;
    private String areaType;
}

