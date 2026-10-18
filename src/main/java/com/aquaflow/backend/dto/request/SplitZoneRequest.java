package com.aquaflow.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitZoneRequest {

    // HORIZONTAL (splits along Y axis) or VERTICAL (splits along X axis)
    @NotNull(message = "Split orientation is required")
    private String orientation;

    // Optional custom split ratio, e.g. 0.5 for equal halves
    @Builder.Default
    private Double ratio = 0.5;

    // Optional custom naming for the 2 parts
    private String part1Name;
    private String part1Code;
    private String part2Name;
    private String part2Code;
}

