package com.aquaflow.backend.domain;

import com.aquaflow.backend.entity.SensorType;

/**
 * Physical-plausibility checks for ArvaNode measurements.
 *
 * <p>These are sensor/physics limits, NOT agronomic AWD thresholds. Ultrasonic limits mirror
 * the JSN-SR04T limits in arvacore_arvanode firmware (20–450 cm). Percent values must be 0–100.
 * Thresholds for irrigation decisions remain field-configured (AutoIrrigationConfig) because
 * the thesis specifies "predefined thresholds" without numeric values.</p>
 */
public final class TelemetryValidator {

    public static final double ULTRASONIC_MIN_CM = 20.0;
    public static final double ULTRASONIC_MAX_CM = 450.0;

    private TelemetryValidator() {}

    public static boolean isPlausible(SensorType type, Double value) {
        if (type == null || value == null || value.isNaN() || value.isInfinite()) {
            return false;
        }
        return switch (type) {
            case SOIL_MOISTURE, HUMIDITY -> value >= 0.0 && value <= 100.0;
            case TEMPERATURE -> value >= -40.0 && value <= 80.0; // DHT22 rated range
            case WATER_LEVEL_DISTANCE -> value >= ULTRASONIC_MIN_CM && value <= ULTRASONIC_MAX_CM;
            case WATER_FLOW, PRESSURE, BATTERY_VOLTAGE -> value >= 0.0;
        };
    }
}

