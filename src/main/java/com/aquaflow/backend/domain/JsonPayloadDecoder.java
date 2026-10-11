package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.DecodedTelemetryPayload;
import com.aquaflow.backend.dto.SignalMetadataDto;
import com.aquaflow.backend.dto.request.TelemetryUplinkRequest;
import com.aquaflow.backend.entity.SensorType;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class JsonPayloadDecoder implements PayloadDecoder {

    @Override
    public boolean supports(TelemetryUplinkRequest request) {
        // Fallback decoder supporting generic JSON / direct HTTP requests
        return true;
    }

    @Override
    public DecodedTelemetryPayload decode(TelemetryUplinkRequest request) {
        DecodedTelemetryPayload payload = new DecodedTelemetryPayload();
        payload.setNodeId(request.getNodeId());
        payload.setDevEui(request.getDevEui());
        payload.setFCnt(request.getFCnt());
        payload.setFPort(request.getFPort());
        payload.setTimestamp(request.getTimestamp() != null ? request.getTimestamp() : LocalDateTime.now());
        // Never fabricate health/signal values: absent fields stay null.
        payload.setBatteryLevel(request.getBatteryLevel());
        payload.setSolarVoltage(request.getSolarVoltage());

        SignalMetadataDto signal = SignalMetadataDto.builder()
                .rssiDbm(request.getRssiDbm())
                .snrDb(request.getSnrDb())
                .gatewayId(request.getGatewayId())
                .frequency(request.getFrequency())
                .build();
        payload.setSignalMetadata(signal);

        Map<SensorType, Double> measurements = new HashMap<>();

        if (request.getMeasurements() != null) {
            request.getMeasurements().forEach((key, value) -> {
                try {
                    SensorType type = SensorType.valueOf(key.toUpperCase());
                    measurements.put(type, value);
                } catch (IllegalArgumentException ignored) {}
            });
        }

        // An empty measurement map is a heartbeat-only uplink; do not invent readings.

        payload.setMeasurements(measurements);
        return payload;
    }
}

