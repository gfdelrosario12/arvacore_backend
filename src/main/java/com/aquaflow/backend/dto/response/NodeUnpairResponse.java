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
public class NodeUnpairResponse {
    private String nodeId;
    private boolean unpaired;
    private String previousStatus;
    private LocalDateTime revokedAt;
    private String message;
}

