-- Migration V21: Provision Six-Node Field Deployment & Terraced Pitak Zones
-- ArvaCore Smart Irrigation Ecosystem (2026)
-- Target Site: 1.1-Hectare Rice Farm, Barangay Silang, Lopez, Quezon
-- Provisions ARVANODE-001 through ARVANODE-006 with deterministic zone mappings (Zones 1 to 6 / Pitaks 1 to 6)

-- 1. Ensure Zone 6 (Pitak 6 - Tail Drainage Basin) exists for field 1
INSERT INTO monitoring_zones (id, name, code, field_id, crop_type, target_moisture_percentage, water_allocation_limit_liters, created_at, updated_at)
SELECT 6, 'Zone 6 (Tail Drain)', 'Q6', 1, 'Rice (NSIC Rc 222)', 70.0, 50000.0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM monitoring_zones WHERE id = 6);

-- Update Zone names to reflect agronomic pitak locations
UPDATE monitoring_zones SET name = 'Zone 1 (Pitak 1 - Upper Inflow)', code = 'Q1', updated_at = CURRENT_TIMESTAMP WHERE id = 1;
UPDATE monitoring_zones SET name = 'Zone 2 (Pitak 2 - North Ridge)', code = 'Q2', updated_at = CURRENT_TIMESTAMP WHERE id = 2;
UPDATE monitoring_zones SET name = 'Zone 3 (Pitak 3 - Center Benchmark)', code = 'Q3', updated_at = CURRENT_TIMESTAMP WHERE id = 3;
UPDATE monitoring_zones SET name = 'Zone 4 (Pitak 4 - Clay Center)', code = 'Q4', updated_at = CURRENT_TIMESTAMP WHERE id = 4;
UPDATE monitoring_zones SET name = 'Zone 5 (Pitak 5 - Distal Flank)', code = 'Q5', updated_at = CURRENT_TIMESTAMP WHERE id = 5;
UPDATE monitoring_zones SET name = 'Zone 6 (Pitak 6 - Tail Drain)', code = 'Q6', updated_at = CURRENT_TIMESTAMP WHERE id = 6;

-- 2. Ensure EdgeNode entities exist for ARVANODE-001 through ARVANODE-006
INSERT INTO edge_nodes (node_id, name, lifecycle_state, health_state, monitoring_zone_id, created_at, updated_at)
SELECT 'ARVANODE-001', 'ArvaNode Field 01', 'COMMISSIONED', 'HEALTHY', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM edge_nodes WHERE node_id = 'ARVANODE-001');

INSERT INTO edge_nodes (node_id, name, lifecycle_state, health_state, monitoring_zone_id, created_at, updated_at)
SELECT 'ARVANODE-002', 'ArvaNode Field 02', 'COMMISSIONED', 'HEALTHY', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM edge_nodes WHERE node_id = 'ARVANODE-002');

INSERT INTO edge_nodes (node_id, name, lifecycle_state, health_state, monitoring_zone_id, created_at, updated_at)
SELECT 'ARVANODE-003', 'ArvaNode Field 03', 'COMMISSIONED', 'HEALTHY', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM edge_nodes WHERE node_id = 'ARVANODE-003');

INSERT INTO edge_nodes (node_id, name, lifecycle_state, health_state, monitoring_zone_id, created_at, updated_at)
SELECT 'ARVANODE-004', 'ArvaNode Field 04', 'COMMISSIONED', 'HEALTHY', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM edge_nodes WHERE node_id = 'ARVANODE-004');

INSERT INTO edge_nodes (node_id, name, lifecycle_state, health_state, monitoring_zone_id, created_at, updated_at)
SELECT 'ARVANODE-005', 'ArvaNode Field 05', 'COMMISSIONED', 'HEALTHY', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM edge_nodes WHERE node_id = 'ARVANODE-005');

INSERT INTO edge_nodes (node_id, name, lifecycle_state, health_state, monitoring_zone_id, created_at, updated_at)
SELECT 'ARVANODE-006', 'ArvaNode Field 06', 'COMMISSIONED', 'HEALTHY', 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM edge_nodes WHERE node_id = 'ARVANODE-006');

-- 3. Assign 1:1 Zone bindings for all 6 nodes
UPDATE edge_nodes SET monitoring_zone_id = 1, lifecycle_state = 'COMMISSIONED', health_state = 'HEALTHY', updated_at = CURRENT_TIMESTAMP WHERE node_id = 'ARVANODE-001';
UPDATE edge_nodes SET monitoring_zone_id = 2, lifecycle_state = 'COMMISSIONED', health_state = 'HEALTHY', updated_at = CURRENT_TIMESTAMP WHERE node_id = 'ARVANODE-002';
UPDATE edge_nodes SET monitoring_zone_id = 3, lifecycle_state = 'COMMISSIONED', health_state = 'HEALTHY', updated_at = CURRENT_TIMESTAMP WHERE node_id = 'ARVANODE-003';
UPDATE edge_nodes SET monitoring_zone_id = 4, lifecycle_state = 'COMMISSIONED', health_state = 'HEALTHY', updated_at = CURRENT_TIMESTAMP WHERE node_id = 'ARVANODE-004';
UPDATE edge_nodes SET monitoring_zone_id = 5, lifecycle_state = 'COMMISSIONED', health_state = 'HEALTHY', updated_at = CURRENT_TIMESTAMP WHERE node_id = 'ARVANODE-005';
UPDATE edge_nodes SET monitoring_zone_id = 6, lifecycle_state = 'COMMISSIONED', health_state = 'HEALTHY', updated_at = CURRENT_TIMESTAMP WHERE node_id = 'ARVANODE-006';

-- 4. Harmonize device credentials for ARVANODE-003 through ARVANODE-006
UPDATE device_credentials
SET bootstrap_key_id = 'bootstrap-ARVANODE-003',
    operational_key_id = 'key-ARVANODE-003',
    credential_state = 'OPERATIONAL',
    updated_at = CURRENT_TIMESTAMP
WHERE node_id = 'ARVANODE-003';

UPDATE device_credentials
SET bootstrap_key_id = 'bootstrap-ARVANODE-004',
    operational_key_id = 'key-ARVANODE-004',
    credential_state = 'OPERATIONAL',
    updated_at = CURRENT_TIMESTAMP
WHERE node_id = 'ARVANODE-004';

UPDATE device_credentials
SET bootstrap_key_id = 'bootstrap-ARVANODE-005',
    operational_key_id = 'key-ARVANODE-005',
    credential_state = 'OPERATIONAL',
    updated_at = CURRENT_TIMESTAMP
WHERE node_id = 'ARVANODE-005';

UPDATE device_credentials
SET bootstrap_key_id = 'bootstrap-ARVANODE-006',
    operational_key_id = 'key-ARVANODE-006',
    credential_state = 'OPERATIONAL',
    updated_at = CURRENT_TIMESTAMP
WHERE node_id = 'ARVANODE-006';
