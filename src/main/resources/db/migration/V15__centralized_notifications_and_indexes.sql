-- Migration V15: Centralized System Notifications & Database Performance Indexes

-- 1. Enrich alerts table to serve as the unified notifications & alerts persistence store
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS title VARCHAR(255);
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS category VARCHAR(64) DEFAULT 'hardware';
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS field_id BIGINT;
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS zone_code VARCHAR(64);
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS actor_id VARCHAR(128);
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS source_name VARCHAR(255);
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS target_scope VARCHAR(128);
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS recommended_action TEXT;
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS dedup_key VARCHAR(255);
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS metadata_json TEXT;

UPDATE alerts
SET title = COALESCE(title, alert_type, 'System Notification'),
    category = COALESCE(category, 'hardware'),
    source_name = COALESCE(source_name, CASE WHEN device_id IS NOT NULL THEN 'Node ' || device_id ELSE 'Centralized Irrigation System' END),
    target_scope = COALESCE(target_scope, CASE WHEN zone_code IS NOT NULL THEN zone_code WHEN device_id IS NOT NULL THEN device_id ELSE 'ENTIRE FIELD' END),
    recommended_action = COALESCE(recommended_action, 'Review system status in ArvaLink dashboard.');

-- 2. Performance Indexes for Database-Driven REST Synchronization & High-Frequency Telemetry
CREATE INDEX IF NOT EXISTS idx_alerts_acknowledged_created_at ON alerts(acknowledged, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_alerts_created_at_desc ON alerts(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_alerts_category_severity ON alerts(category, alert_level, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_alerts_dedup_key_created ON alerts(dedup_key, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_alerts_device_id ON alerts(device_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_alerts_field_id ON alerts(field_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_telemetry_readings_node_ts ON telemetry_readings(edge_node_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_telemetry_readings_field_ts ON telemetry_readings(field_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_telemetry_stream_received_at ON telemetry_stream_events(received_at DESC);
CREATE INDEX IF NOT EXISTS idx_telemetry_stream_node_received ON telemetry_stream_events(node_id, received_at DESC);
CREATE INDEX IF NOT EXISTS idx_edge_nodes_zone_lifecycle ON edge_nodes(monitoring_zone_id, lifecycle_state);
CREATE INDEX IF NOT EXISTS idx_node_pairing_status_req_at ON node_pairing_requests(pairing_status, requested_at DESC);
CREATE INDEX IF NOT EXISTS idx_node_pairing_node_req_at ON node_pairing_requests(node_id, requested_at DESC);

