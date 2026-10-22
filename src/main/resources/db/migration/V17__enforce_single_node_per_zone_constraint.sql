-- Migration V17: Enforce 1 Zone = 1 Node maximum constraint
-- Compatible across PostgreSQL and H2 test databases

CREATE INDEX IF NOT EXISTS idx_edge_nodes_zone_active
ON edge_nodes (monitoring_zone_id, lifecycle_state);
