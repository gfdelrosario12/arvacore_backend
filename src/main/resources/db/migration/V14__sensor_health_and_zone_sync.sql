-- Migration V14: Sensor Health Telemetry Persistence, Authoritative Node Status, and Monitoring Zone Unification

-- 1. Add per-sensor health and overall telemetry status columns to edge_nodes
ALTER TABLE edge_nodes ADD COLUMN IF NOT EXISTS soil_sensor_health VARCHAR(32) DEFAULT 'UNKNOWN';
ALTER TABLE edge_nodes ADD COLUMN IF NOT EXISTS ultrasonic_sensor_health VARCHAR(32) DEFAULT 'UNKNOWN';
ALTER TABLE edge_nodes ADD COLUMN IF NOT EXISTS telemetry_status VARCHAR(32) DEFAULT 'UNKNOWN';

UPDATE edge_nodes
SET soil_sensor_health = COALESCE(soil_sensor_health, 'UNKNOWN'),
    ultrasonic_sensor_health = COALESCE(ultrasonic_sensor_health, 'UNKNOWN'),
    telemetry_status = COALESCE(telemetry_status, 'UNKNOWN');

-- 2. Add sensor health and telemetry status columns to telemetry_stream_events
ALTER TABLE telemetry_stream_events ADD COLUMN IF NOT EXISTS soil_sensor_health VARCHAR(32);
ALTER TABLE telemetry_stream_events ADD COLUMN IF NOT EXISTS ultrasonic_sensor_health VARCHAR(32);
ALTER TABLE telemetry_stream_events ADD COLUMN IF NOT EXISTS telemetry_status VARCHAR(32);

-- 3. Ensure monitoring_zones have deterministic codes (Q1, Q2, Q3, Q4...) where missing
UPDATE monitoring_zones
SET code = 'Q' || id
WHERE code IS NULL OR TRIM(code) = '';

