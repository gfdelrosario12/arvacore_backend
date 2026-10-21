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

    public static final double ULTRASONIC_MIN_CM = 2.0;
    public static final double ULTRASONIC_MAX_CM = 450.0;

    private TelemetryValidator() {}

    /**
     * Checks whether a received measurement is acceptable for ingestion and storage.
     * Note: 0.0 is explicitly permitted for SOIL_MOISTURE and WATER_LEVEL_DISTANCE
     * because the ESP32 firmware transmits numeric 0.0 as a sensor failure placeholder
     * while separately reporting sensorHealth = ERROR/DISCONNECTED/TIMEOUT.
     */
    public static boolean isPlausible(SensorType type, Double value) {
        if (type == null || value == null || value.isNaN() || value.isInfinite()) {
            return false;
        }
        return switch (type) {
            case SOIL_MOISTURE, HUMIDITY -> value >= 0.0 && value <= 100.0;
            case TEMPERATURE -> value >= -40.0 && value <= 80.0; // DHT22 rated range
            case WATER_LEVEL_DISTANCE -> value == 0.0 || (value >= ULTRASONIC_MIN_CM && value <= ULTRASONIC_MAX_CM);
            case WATER_FLOW, PRESSURE, BATTERY_VOLTAGE -> value >= 0.0;
        };
    }

    /**
     * Checks whether a measurement represents a genuinely healthy sensor reading
     * suitable for zone aggregations, irrigation decisions, and accuracy evaluations.
     */
    public static boolean isHealthyMeasurement(SensorType type, Double value, String sensorHealth) {
        if (!isPlausible(type, value)) {
            return false;
        }
        if (sensorHealth != null && !sensorHealth.isBlank()
                && !"OK".equalsIgnoreCase(sensorHealth.trim())
                && !"UNKNOWN".equalsIgnoreCase(sensorHealth.trim())) {
            return false;
        }
        if (type == SensorType.WATER_LEVEL_DISTANCE && value < ULTRASONIC_MIN_CM) {
            return false;
        }
        return true;
    }
}

