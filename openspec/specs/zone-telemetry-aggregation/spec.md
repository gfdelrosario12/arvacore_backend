# zone-telemetry-aggregation Specification

## Purpose

Provides monitoring-zone telemetry aggregation in AquaFlow by computing zone-level metrics, health scores, spatial weighting, deterministic handling of missing/stale/offline nodes, and trend time-series query endpoints.

## Requirements

### Requirement: System SHALL compute aggregated telemetry metrics for monitoring zones
The system SHALL aggregate individual edge node telemetry readings assigned to a monitoring zone into cohesive zone-level metrics including average/weighted soil moisture, average/weighted temperature, average humidity, water level, battery voltage, minimum RSSI, minimum SNR, online node count, stale node count, and offline node count.

#### Scenario: Aggregate zone telemetry from online active nodes
- **WHEN** a zone aggregation request is evaluated for a zone with active online nodes
- **THEN** system computes zone soil moisture, temperature, humidity, water level, battery, RSSI, and SNR metrics across active nodes and records online/stale/offline node counts

#### Scenario: Apply spatial weighting to node readings
- **WHEN** edge nodes assigned to a zone specify spatial weighting values (weights > 0)
- **THEN** system computes weighted averages for soil moisture and temperature using node spatial weights divided by total active node weight

### Requirement: System SHALL deterministically handle missing, stale, conflicting, and offline node telemetry
The system SHALL classify telemetry readings older than a configurable staleness threshold as stale, exclude offline/decommissioned nodes from primary averages, and fall back to valid active nodes or default indicators when node data is missing or conflicting.

#### Scenario: Exclude stale node readings from primary zone averages
- **WHEN** an assigned edge node's last telemetry reading timestamp is older than the configured staleness threshold (e.g. 30 minutes)
- **THEN** system marks the node as stale, increments `staleNodeCount`, excludes its stale values from real-time zone averages, and includes it in stale node diagnostic metrics

#### Scenario: Handle completely offline or unassigned zone
- **WHEN** a zone has no active online edge nodes or all assigned nodes are offline/stale
- **THEN** system marks zone health status as `OFFLINE` or `ATTENTION_REQUIRED`, sets aggregated telemetry values to null/fallback indicators, and reports zero active nodes

### Requirement: System SHALL evaluate composite zone health status
The system SHALL calculate a composite health status for each zone (`OPTIMAL`, `ATTENTION_REQUIRED`, `CRITICAL`, `OFFLINE`) based on soil moisture bounds, water level thresholds, battery levels, RSSI strength, and ratio of online to offline nodes.

#### Scenario: Determine zone health status OPTIMAL
- **WHEN** all active nodes are online, soil moisture and water levels are within target operational ranges, and battery/signal strength exceed warning thresholds
- **THEN** system assigns zone health status `OPTIMAL`

#### Scenario: Determine zone health status CRITICAL
- **WHEN** soil moisture drops below critical wilting threshold or water level falls below critical operational minimum or majority of nodes are offline
- **THEN** system assigns zone health status `CRITICAL`

### Requirement: System SHALL provide zone telemetry trend endpoint and dashboard aggregation queries
The system SHALL provide REST endpoint `GET /api/v1/fields/{fieldId}/zones/{zoneId}/trend` to query historical aggregated zone telemetry trends over requested time windows (e.g. 24h, 7d) and support field-level zone dashboard summary queries.

#### Scenario: Retrieve zone telemetry trend time-series
- **WHEN** client requests `GET /api/v1/fields/{fieldId}/zones/{zoneId}/trend` with valid start and end timestamps
- **THEN** system returns time-series data points containing aggregated zone soil moisture, temperature, water level, and health status over the requested window

### Requirement: System SHALL convert ultrasonic distance to pond water depth
The system SHALL ingest physical ultrasonic distance readings (`WATER_LEVEL_DISTANCE` from JSN-SR04T sensors) and convert them to calibrated pond water depth $h$ using the sensor datum offset formula:
$$h = \max(0.0, 30.0 - \text{distance}_{\text{cm}})$$
The calculated depth SHALL be aggregated into `waterLevel` for the zone and reported in `ZoneResponse` and `EdgeNodeResponse` without fabricating readings when distance is invalid or out of bounds.

#### Scenario: Aggregating ultrasonic pond depth
- **WHEN** an assigned edge node transmits an ultrasonic distance measurement of 22.0 cm
- **THEN** system converts this to a water depth of 8.0 cm and aggregates it into the zone's active water level

### Requirement: Zone service SHALL fall back to monitoring zones and enrich with active node IDs
The zone service SHALL fall back to querying the `monitoring_zones` table when the legacy `zones` table is empty, and enrich each returned `ZoneResponse` with assigned edge node IDs, live aggregate soil moisture, pond depth, battery level, and online status.

#### Scenario: Querying zones when legacy table is unpopulated
- **WHEN** client executes `GET /api/v1/zones`
- **THEN** system queries configured `MonitoringZone` entities, attaches all assigned active edge node IDs, computes current aggregate telemetry, and returns HTTP 200

