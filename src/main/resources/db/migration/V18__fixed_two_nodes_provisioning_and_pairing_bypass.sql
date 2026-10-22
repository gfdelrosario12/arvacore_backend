-- Migration V18: Fixed Two-Node Operational Credentials & Telemetry Setup
-- Promotes ARVANODE-001 and ARVANODE-002 directly to OPERATIONAL state
-- Re-links any telemetry from historical node aliases to ARVANODE-001

-- 1. Ensure credentials exist and are OPERATIONAL (portable: no ON CONFLICT)
UPDATE device_credentials SET
    bootstrap_key_id = 'key-ARVANODE-001', bootstrap_secret = 'dev_sec_e62959d17ffe8ad6f98246918bd61fa4e87b7b8963cf44cc15cb5c18',
    operational_key_id = 'key-ARVANODE-001', operational_secret = 'dev_sec_e62959d17ffe8ad6f98246918bd61fa4e87b7b8963cf44cc15cb5c18',
    credential_state = 'OPERATIONAL', updated_at = CURRENT_TIMESTAMP
WHERE node_id = 'ARVANODE-001';

INSERT INTO device_credentials (node_id, bootstrap_key_id, bootstrap_secret, operational_key_id, operational_secret, credential_state, installation_id, created_at, updated_at)
SELECT 'ARVANODE-001', 'key-ARVANODE-001', 'dev_sec_e62959d17ffe8ad6f98246918bd61fa4e87b7b8963cf44cc15cb5c18', 'key-ARVANODE-001', 'dev_sec_e62959d17ffe8ad6f98246918bd61fa4e87b7b8963cf44cc15cb5c18', 'OPERATIONAL', 'default', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM device_credentials WHERE node_id = 'ARVANODE-001');

UPDATE device_credentials SET
    bootstrap_key_id = 'key-ARVANODE-002', bootstrap_secret = 'dev_sec_67c3f4b5b6f18e4c9d5e7d0a03a1ef1da7d5aafc56dfe643e39083f5',
    operational_key_id = 'key-ARVANODE-002', operational_secret = 'dev_sec_67c3f4b5b6f18e4c9d5e7d0a03a1ef1da7d5aafc56dfe643e39083f5',
    credential_state = 'OPERATIONAL', updated_at = CURRENT_TIMESTAMP
WHERE node_id = 'ARVANODE-002';

INSERT INTO device_credentials (node_id, bootstrap_key_id, bootstrap_secret, operational_key_id, operational_secret, credential_state, installation_id, created_at, updated_at)
SELECT 'ARVANODE-002', 'key-ARVANODE-002', 'dev_sec_67c3f4b5b6f18e4c9d5e7d0a03a1ef1da7d5aafc56dfe643e39083f5', 'key-ARVANODE-002', 'dev_sec_67c3f4b5b6f18e4c9d5e7d0a03a1ef1da7d5aafc56dfe643e39083f5', 'OPERATIONAL', 'default', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM device_credentials WHERE node_id = 'ARVANODE-002');

-- 2. Ensure EdgeNode entries exist in edge_nodes table as COMMISSIONED
INSERT INTO edge_nodes (node_id, identity_type, identity_value, lifecycle_state, health_state, hardware_model, firmware_version, created_at, updated_at, version)
SELECT 'ARVANODE-001', 'SERIAL_NUMBER', 'ARVANODE-001', 'COMMISSIONED', 'HEALTHY', 'ESP32-WROOM-32', '2.0.0', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM edge_nodes WHERE node_id = 'ARVANODE-001');

UPDATE edge_nodes 
SET lifecycle_state = 'COMMISSIONED', health_state = 'HEALTHY', updated_at = CURRENT_TIMESTAMP
WHERE node_id = 'ARVANODE-001' AND lifecycle_state IN ('UNREGISTERED', 'DECOMMISSIONED');

INSERT INTO edge_nodes (node_id, identity_type, identity_value, lifecycle_state, health_state, hardware_model, firmware_version, created_at, updated_at, version)
SELECT 'ARVANODE-002', 'SERIAL_NUMBER', 'ARVANODE-002', 'COMMISSIONED', 'HEALTHY', 'ESP32-WROOM-32', '2.0.0', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM edge_nodes WHERE node_id = 'ARVANODE-002');

UPDATE edge_nodes 
SET lifecycle_state = 'COMMISSIONED', health_state = 'HEALTHY', updated_at = CURRENT_TIMESTAMP
WHERE node_id = 'ARVANODE-002' AND lifecycle_state IN ('UNREGISTERED', 'DECOMMISSIONED');
