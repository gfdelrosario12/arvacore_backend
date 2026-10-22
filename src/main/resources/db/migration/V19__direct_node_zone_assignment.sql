-- Migration V19: Direct Node-to-Zone Assignment
-- Permanently assigns ARVANODE-001 -> Zone 1 (Q1, id=1)
--                     ARVANODE-002 -> Zone 2 (Q2, id=2)
-- Ensures zones have correct codes and nodes are COMMISSIONED.
-- This assignment is immutable; no pairing or reassignment workflow is needed.

-- 1. Ensure Zone 1 (Q1) and Zone 2 (Q2) have codes set
UPDATE monitoring_zones SET code = 'Q1', updated_at = CURRENT_TIMESTAMP
WHERE id = 1 AND (code IS NULL OR code = '');

UPDATE monitoring_zones SET code = 'Q2', updated_at = CURRENT_TIMESTAMP
WHERE id = 2 AND (code IS NULL OR code = '');

-- 2. Assign ARVANODE-001 to Zone 1 (Q1, id=1)
UPDATE edge_nodes
SET monitoring_zone_id = 1,
    lifecycle_state    = 'COMMISSIONED',
    health_state       = 'HEALTHY',
    updated_at         = CURRENT_TIMESTAMP
WHERE node_id = 'ARVANODE-001';

-- 3. Assign ARVANODE-002 to Zone 2 (Q2, id=2)
UPDATE edge_nodes
SET monitoring_zone_id = 2,
    lifecycle_state    = 'COMMISSIONED',
    health_state       = 'HEALTHY',
    updated_at         = CURRENT_TIMESTAMP
WHERE node_id = 'ARVANODE-002';

-- 4. Clear any stale zone assignments from other nodes pointing at zones 1 or 2
--    (enforces 1:1 mapping — only our two provisioned nodes may hold these zones)
UPDATE edge_nodes
SET monitoring_zone_id = NULL,
    updated_at         = CURRENT_TIMESTAMP
WHERE monitoring_zone_id IN (1, 2)
  AND node_id NOT IN ('ARVANODE-001', 'ARVANODE-002');


-- 5. Harmonize operational and bootstrap key IDs to match firmware convention
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
