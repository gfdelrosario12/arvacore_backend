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
                if (value == null) return;
                String normalizedKey = key.toUpperCase().trim().replace("-", "_");
                if (normalizedKey.equals("SOIL_MOISTURE") || normalizedKey.equals("SOILMOISTURE") || normalizedKey.equals("SOIL_MOISTURE_PERCENT") || normalizedKey.equals("SOILMOISTUREPERCENT")) {
                    measurements.put(SensorType.SOIL_MOISTURE, value);
                } else if (normalizedKey.equals("WATER_LEVEL_DISTANCE") || normalizedKey.equals("WATERLEVELDISTANCE") || normalizedKey.equals("DISTANCE")) {
                    measurements.put(SensorType.WATER_LEVEL_DISTANCE, value);
                } else if (normalizedKey.equals("BATTERY_VOLTAGE") || normalizedKey.equals("BATTERYVOLTAGE") || normalizedKey.equals("VOLTAGE")) {
                    measurements.put(SensorType.BATTERY_VOLTAGE, value);
                } else {
                    try {
                        SensorType type = SensorType.valueOf(normalizedKey);
                        measurements.put(type, value);
                    } catch (IllegalArgumentException ignored) {}
                }
            });
        }

        // Support direct top-level fields sent by firmware if not in measurements map
        if (!measurements.containsKey(SensorType.SOIL_MOISTURE) && request.getSoilMoisturePercent() != null) {
            measurements.put(SensorType.SOIL_MOISTURE, request.getSoilMoisturePercent());
        }
        if (!measurements.containsKey(SensorType.BATTERY_VOLTAGE) && request.getBatteryVoltage() != null) {
            measurements.put(SensorType.BATTERY_VOLTAGE, request.getBatteryVoltage());
        }

        payload.setMeasurements(measurements);
        return payload;
    }
}

