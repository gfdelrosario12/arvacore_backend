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
public class PendingCommandResponse {
    private String correlationId;
    private String command; // START_IRRIGATION, STOP_IRRIGATION, SAFE_STOP, STATUS_REQUEST
    private String targetControllerId;
    private Long targetFieldId;
    private String reason;
    private Integer durationMinutes;
    private LocalDateTime timestamp;
    private LocalDateTime expiry;
    private String payloadJson;
}
