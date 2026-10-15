-- Migration V8: Telemetry Field Attribution & 30-Minute Pairing Expiration
-- Persists field attribution directly on telemetry readings.
-- Enforces authoritative 30-minute expiration for pending pairing requests.

ALTER TABLE telemetry_readings ADD COLUMN IF NOT EXISTS field_id BIGINT;
ALTER TABLE telemetry_readings ADD CONSTRAINT fk_telemetry_field FOREIGN KEY (field_id) REFERENCES fields(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_telemetry_field_time ON telemetry_readings(field_id, timestamp DESC);

ALTER TABLE node_pairing_requests ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_pairing_expires_at ON node_pairing_requests(expires_at DESC);

-- Backfill expires_at for existing requests (30 minutes after requested_at)
UPDATE node_pairing_requests
SET expires_at = requested_at + INTERVAL '30' MINUTE
WHERE expires_at IS NULL;

-- Backfill field_id for existing readings from assigned edge node monitoring zone
UPDATE telemetry_readings
SET field_id = (
    SELECT mz.field_id
    FROM edge_nodes en
    JOIN monitoring_zones mz ON en.monitoring_zone_id = mz.id
    WHERE en.id = telemetry_readings.edge_node_id
)
WHERE field_id IS NULL;
