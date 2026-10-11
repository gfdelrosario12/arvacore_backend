package com.aquaflow.backend.entity;

/**
 * Measurement types accepted by the ArvaCore backend.
 *
 * <p>WATER_LEVEL_DISTANCE is the raw JSN-SR04T ultrasonic sensor-to-surface distance (cm)
 * reported by ArvaNodes. It is NOT water depth: converting to depth requires a per-node
 * mounting reference that the thesis leaves to field calibration (see openspec change
 * integrate-arvacore-ecosystem).</p>
 */
public enum SensorType {
    SOIL_MOISTURE,
    TEMPERATURE,
    HUMIDITY,
    WATER_FLOW,
    PRESSURE,
    BATTERY_VOLTAGE,
    WATER_LEVEL_DISTANCE
}
