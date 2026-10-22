-- Migration V20: Harmonize Fixed Node Credential Key Identifiers
-- Ensures bootstrap_key_id matches 'bootstrap-ARVANODE-00X' and
-- operational_key_id matches 'key-ARVANODE-00X' to ensure seamless
-- ARVA-HMAC-V1 authentication for both bootstrap and operational payloads.

UPDATE device_credentials
SET bootstrap_key_id = 'bootstrap-ARVANODE-001',
    operational_key_id = 'key-ARVANODE-001',
    credential_state = 'OPERATIONAL',
    updated_at = CURRENT_TIMESTAMP
WHERE node_id = 'ARVANODE-001';

UPDATE device_credentials
SET bootstrap_key_id = 'bootstrap-ARVANODE-002',
    operational_key_id = 'key-ARVANODE-002',
    credential_state = 'OPERATIONAL',
    updated_at = CURRENT_TIMESTAMP
WHERE node_id = 'ARVANODE-002';
