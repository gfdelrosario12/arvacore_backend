# device-management Specification

## Purpose
Defines edge device lifecycle management, registration, commissioning, health tracking, and field/zone topology assignment across the ArvaCore ecosystem.

## Requirements
### Requirement: System SHALL register edge devices
The system SHALL register new edge devices with unique device ID, hardware model, firmware version, and associated zones.

#### Scenario: Register new edge device
- **WHEN** a new edge device connects and provides registration details
- **THEN** system creates a device record and associates it with configured zones

### Requirement: System SHALL monitor device heartbeat
The system SHALL track device heartbeat signals and mark devices as offline after configurable timeout.

#### Scenario: Device heartbeat timeout
- **WHEN** device fails to send heartbeat within timeout period
- **THEN** system marks the device as offline and generates an alert

### Requirement: System SHALL track device status
The system SHALL maintain current status (online, offline, maintenance, error) for all registered devices.

#### Scenario: Query device status
- **WHEN** operations team queries device status
- **THEN** system returns current status with last seen timestamp

### Requirement: System SHALL manage device firmware
The system SHALL support firmware version tracking and target version assignment for over-the-air updates.

#### Scenario: Assign firmware update
- **WHEN** administrator assigns a firmware target version to a device group
- **THEN** system records the target version and schedules update rollout

### Requirement: System SHALL represent edge node lifecycle and communication identity
The system SHALL represent `EdgeNode` with hardware identity (MAC address, serial number, hardware model), active status (`ONLINE`, `OFFLINE`, `DEGRADED`, `MAINTENANCE`), health metrics (battery level, solar voltage, signal strength), firmware version, and assignment to a `MonitoringZone`.

#### Scenario: Edge node status telemetry update
- **WHEN** edge node reports heartbeat and health status
- **THEN** system updates `EdgeNode` status, health metrics, and last seen timestamp in persistence

### Requirement: System SHALL represent read-oriented monitoring zones
The system SHALL aggregate edge nodes and monitoring points into `MonitoringZone` entities, providing read-oriented summary views of assigned nodes, active points, and aggregate telemetry.

#### Scenario: Query monitoring zone aggregation
- **WHEN** client queries `MonitoringZone` by ID
- **THEN** system returns zone detail aggregating assigned edge nodes and monitoring points

### Requirement: System SHALL manage EdgeNode lifecycle transitions
The system SHALL maintain formal lifecycle states for `EdgeNode` entities (`UNREGISTERED`, `PROVISIONED`, `COMMISSIONED`, `ACTIVE`, `DECOMMISSIONED`, `REPLACED`) and enforce valid state transition workflows (`commission`, `decommission`, `replace`).

#### Scenario: Commission edge node
- **WHEN** client submits POST `/api/v1/nodes/{id}/commission` with valid commissioning parameters
- **THEN** system transitions node lifecycle state from `PROVISIONED` to `COMMISSIONED`, generates a single-use commissioning credential token, and returns the response without exposing raw cryptographic secrets

#### Scenario: Decommission edge node
- **WHEN** client submits POST `/api/v1/nodes/{id}/decommission`
- **THEN** system transitions node state to `DECOMMISSIONED`, unassigns active monitoring points, and invalidates active edge session tokens

#### Scenario: Replace faulty edge node
- **WHEN** client submits POST `/api/v1/nodes/{id}/replace` with replacement node ID
- **THEN** system transfers monitoring point assignments and configuration from faulty node to replacement node, marks original node as `REPLACED`, and commissions replacement node

### Requirement: System SHALL configure transmission parameters
The system SHALL support updating transmission frequency interval (seconds), transmit power level (dBm), and duty cycle percentage on `EdgeNode` entities.

#### Scenario: Update transmission configuration
- **WHEN** client submits PUT `/api/v1/nodes/{id}/transmission` with valid transmission parameters
- **THEN** system updates the node's transmission properties and flags configuration for edge node sync

### Requirement: System SHALL expose node health and telemetry queries
The system SHALL provide endpoints for querying node health status, latest telemetry readings, and paginated historical telemetry.

#### Scenario: Query node health metrics
- **WHEN** client requests GET `/api/v1/nodes/{id}/health`
- **THEN** system returns battery level, solar voltage, signal strength (dBm), and last heartbeat timestamp for the node

#### Scenario: Query node latest and historical telemetry
- **WHEN** client requests GET `/api/v1/nodes/{id}/telemetry/latest` or GET `/api/v1/nodes/{id}/telemetry/history`
- **THEN** system returns latest or paginated historical telemetry readings recorded by the node's assigned monitoring points

### Requirement: System SHALL update EdgeNode status and health metrics upon telemetry ingestion
The system SHALL automatically update `EdgeNode` last-seen heartbeat timestamp, battery level, solar voltage, and signal strength (RSSI/SNR) upon successful telemetry ingestion.

#### Scenario: Update node heartbeat and battery metrics on valid telemetry
- **WHEN** telemetry ingestion pipeline processes a valid uplink containing battery voltage, solar voltage, and signal metrics
- **THEN** system updates the corresponding `EdgeNode` `healthMetrics` and refreshes `lastHeartbeat` to current timestamp

#### Scenario: Maintain node health state based on signal and battery threshold
- **WHEN** telemetry payload reports battery level below critical threshold or severely degraded signal RSSI
- **THEN** system updates node `healthState` to `DEGRADED` or `CRITICAL` according to operational parameters

### Requirement: System SHALL expose REST endpoints for edge node health monitoring and freshness inspection
The system SHALL expose REST endpoints under `/api/v1/devices/nodes/health` to retrieve node health summaries, telemetry freshness indicators, and health metrics history.

#### Scenario: Retrieve edge node health summary
- **WHEN** client sends GET `/api/v1/devices/nodes/health`
- **THEN** system returns health summaries for all registered edge nodes including status, battery, RSSI, and last seen timestamp

#### Scenario: Retrieve specific edge node health detail
- **WHEN** client sends GET `/api/v1/devices/nodes/{id}/health`
- **THEN** system returns detailed health metrics, freshness status, and communication failure history for the target node

### Requirement: System SHALL support dynamic edge node field and zone reassignment
The system SHALL provide an administrative endpoint (`PUT /api/v1/nodes/{id}/field-assignment`) permitting operators to reassign an edge node to a specific agricultural field and monitoring zone, updating relational bindings and reflecting updated field topology immediately in subsequent queries.

#### Scenario: Reassign edge node to different field and zone
- **WHEN** an authorized operator submits `PUT /api/v1/nodes/{id}/field-assignment` with target `fieldId` and `monitoringZoneId`
- **THEN** the system updates the edge node's zone relationship, verifies field containment, and returns HTTP 200 with the node's updated field and zone descriptors

