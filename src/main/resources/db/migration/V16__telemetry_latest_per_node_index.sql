-- Migration V16: Composite index for latest-telemetry-per-node lookups
--
-- The ArvaLink Home Dashboard polls the latest telemetry snapshot for every
-- visible node on a 5 second interval. The lookup is always scoped to a set of
-- edge nodes and always resolves the newest reading per (node, sensor_type)
-- pair, so the existing (edge_node_id, timestamp DESC) index cannot be used to
-- seek straight to the newest row of each sensor type without scanning every
-- historical row of the node.
--
-- This composite index lets the batched "latest reading per node and sensor
-- type" query resolve through a single index probe per (node, sensor_type)
-- pair instead of scanning the whole telemetry history of each node.

CREATE INDEX IF NOT EXISTS idx_telemetry_readings_node_type_ts
    ON telemetry_readings(edge_node_id, sensor_type, timestamp DESC);
