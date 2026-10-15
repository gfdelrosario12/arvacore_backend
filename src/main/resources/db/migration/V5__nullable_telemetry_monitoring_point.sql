-- Migration V5: Allow telemetry readings to exist with or without an assigned monitoring point

ALTER TABLE telemetry_readings ALTER COLUMN monitoring_point_id DROP NOT NULL;
