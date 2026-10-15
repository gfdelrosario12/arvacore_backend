-- Migration V7: Seed Default Field and AWD Monitoring Zones
-- Seeds canonical field and 5 monitoring zones for AWD sensor node pairing and irrigation management.

INSERT INTO fields (id, name, boundary_geojson, area_hectares, created_at, updated_at)
SELECT 1, 'Main Rice Field', '{"type":"Polygon","coordinates":[[[121.242,14.152],[121.244,14.152],[121.244,14.154],[121.242,14.154],[121.242,14.152]]]}', 2.5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM fields WHERE id = 1);

INSERT INTO monitoring_zones (id, name, field_id, crop_type, target_moisture_percentage, water_allocation_limit_liters, created_at, updated_at)
SELECT 1, 'Zone 1 (Q1)', 1, 'Rice (NSIC Rc 222)', 70.0, 50000.0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM monitoring_zones WHERE id = 1);

INSERT INTO monitoring_zones (id, name, field_id, crop_type, target_moisture_percentage, water_allocation_limit_liters, created_at, updated_at)
SELECT 2, 'Zone 2 (Q2)', 1, 'Rice (NSIC Rc 222)', 70.0, 50000.0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM monitoring_zones WHERE id = 2);

INSERT INTO monitoring_zones (id, name, field_id, crop_type, target_moisture_percentage, water_allocation_limit_liters, created_at, updated_at)
SELECT 3, 'Zone 3 (Q3)', 1, 'Rice (NSIC Rc 222)', 70.0, 50000.0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM monitoring_zones WHERE id = 3);

INSERT INTO monitoring_zones (id, name, field_id, crop_type, target_moisture_percentage, water_allocation_limit_liters, created_at, updated_at)
SELECT 4, 'Zone 4 (Q4)', 1, 'Rice (NSIC Rc 222)', 70.0, 50000.0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM monitoring_zones WHERE id = 4);

INSERT INTO monitoring_zones (id, name, field_id, crop_type, target_moisture_percentage, water_allocation_limit_liters, created_at, updated_at)
SELECT 5, 'Zone 5 (East)', 1, 'Rice (NSIC Rc 222)', 70.0, 50000.0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM monitoring_zones WHERE id = 5);

ALTER TABLE fields ALTER COLUMN id RESTART WITH 100;
ALTER TABLE monitoring_zones ALTER COLUMN id RESTART WITH 100;
ALTER TABLE auto_irrigation_configs ALTER COLUMN schedule_mode DROP NOT NULL;

