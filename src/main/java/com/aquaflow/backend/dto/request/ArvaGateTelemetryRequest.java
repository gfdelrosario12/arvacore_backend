package com.aquaflow.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArvaGateTelemetryRequest {
    @NotBlank
    private String deviceId;

    private List<GateTelemetryItem> gates;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GateTelemetryItem {
        private Integer gateIndex;
        private String state;
        private Boolean isActuating;
        private Boolean physicalConfirmed;
    }
}

