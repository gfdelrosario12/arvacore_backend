package com.aquaflow.backend.dto.response;

import com.aquaflow.backend.entity.ArvaGate;
import com.aquaflow.backend.entity.ArvaGateDevice;
import com.aquaflow.backend.entity.ArvaGateEvent;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArvaGateStatusResponse {
    private ArvaGateDevice device;
    private List<ArvaGate> gates;
    private List<ArvaGateEvent> recentEvents;
    private LocalDateTime serverTime;
}

