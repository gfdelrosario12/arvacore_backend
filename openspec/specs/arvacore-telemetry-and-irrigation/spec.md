# arvacore-telemetry-and-irrigation Specification

## Purpose
Defines backend contracts and domain requirements for physical IoT telemetry ingestion, gateway security authentication, closed-loop irrigation flow delivery verification, authoritative 30-minute node pairing lifecycle enforcement, field-level telemetry attribution snapshots, and high-frequency 3-second node telemetry support.

## Requirements
### Requirement: System SHALL ingest truthful field telemetry without fabricated defaults
The system SHALL ingest physical node telemetry from ArvaNodes through gateway endpoints (`POST /api/v1/telemetry/uplink/{nodeId}`), validate physical sensor bounds (ultrasonic distance 20–450 cm, soil moisture 0–100%, battery voltage 2.5–5.0V), reject implausible measurements, and never inject synthetic default sensor readings.

#### Scenario: Ingest valid physical sensor telemetry
- **WHEN** an ArvaNode transmits valid distance, water level, and soil moisture measurements with valid gateway credentials
- **THEN** the system decodes the payload, validates physical sensor plausibility, persists `TelemetryReading` entities, and returns HTTP 200

#### Scenario: Reject out-of-bounds telemetry measurements
- **WHEN** an uplink contains soil moisture < 0% or > 100%, or ultrasonic distance < 20 cm or > 450 cm
- **THEN** the system rejects the implausible measurement channel without fabricating fallback readings

### Requirement: System SHALL support mandatory 3-second telemetry transmission policy
The system SHALL ingest high-frequency telemetry transmitted by active field nodes operating under the mandatory 3-second transmission policy (`TELEMETRY_INTERVAL_MS = 3000`) without applying throttling, rate-limit drops, or synthetic smoothing to genuine physical readings.

#### Scenario: Ingest high-frequency telemetry sequence
- **WHEN** an activated ArvaNode delivers sequential uplinks spaced at 3-second intervals
- **THEN** the system persists each sequential reading with its unique sequence number and timestamp and broadcasts updates to real-time subscribers

### Requirement: System SHALL authenticate gateway telemetry via constant-time key validation
The system SHALL enforce gateway token authentication using the `X-ArvaCore-Gateway-Key` header on all telemetry endpoints (`/api/v1/telemetry/**`) and gateway-facing pairing status queries (`/api/v1/pairing/status/**`).

#### Scenario: Accept uplink with valid gateway key
- **WHEN** a client POSTs telemetry with header `X-ArvaCore-Gateway-Key: arvacore-gateway-sec-key-2026`
- **THEN** the system validates the key in constant time and allows telemetry ingestion

#### Scenario: Reject uplink with missing or invalid gateway key
- **WHEN** a client POSTs telemetry with an incorrect or missing `X-ArvaCore-Gateway-Key` header
- **THEN** the system rejects the request with HTTP 401 Unauthorized

### Requirement: System SHALL enforce closed-loop flow delivery verification for central irrigation
The system SHALL orchestrate irrigation commands exclusively for the centralized pump and solenoid valve, verifying water delivery through the inline flow sensor.

#### Scenario: Distinguish pump activation from physical water delivery
- **WHEN** central pump is commanded ON and inline flow sensor confirms water flow $\ge 5.0\,\text{L/min}$
- **THEN** the system marks the command outcome as physically verified

#### Scenario: Detect dry-run or pump blockage
- **WHEN** central pump is commanded ON but inline flow sensor indicates $0.0\,\text{L/min}$ after the verification window
- **THEN** the system flags an operational fault and raises a dry-run anomaly alert

### Requirement: System SHALL enforce authoritative 30-minute node pairing expiration and deduplication
The system SHALL govern physical node pairing requests with a strict 30-minute expiration window calculated from initial request creation (`expiresAt = requestedAt + 30 minutes`). Repeated pairing requests from the same node SHALL return the active request without resetting the 30-minute expiration window. Any pairing approval attempt after expiration MUST be rejected with HTTP 400.

#### Scenario: Pairing approval within valid 30-minute window
- **WHEN** an administrator approves a pairing request before its `expiresAt` timestamp
- **THEN** the system commissions the edge node, associates it with the designated field and monitoring zone, transitions pairing status to `PAIRED`, and returns HTTP 200

#### Scenario: Rejecting pairing approval for expired request
- **WHEN** an administrator attempts to approve a pairing request whose `expiresAt` is in the past
- **THEN** the system marks the request as `EXPIRED`, rejects the operation with HTTP 400 ("Pairing request has expired"), and preserves the node in uncommissioned state

#### Scenario: Deduplicating repeated pairing requests within the active window
- **WHEN** a node broadcasts multiple pairing requests while an unapproved request exists within 30 minutes
- **THEN** the system updates metadata (such as IP or device name) while preserving the original `requestedAt` and `expiresAt` timestamps without resetting the 30-minute timer

### Requirement: System SHALL capture immutable field-level telemetry attribution and support dynamic node field assignment
The system SHALL persist the associated `field_id` directly on each ingested `TelemetryReading` record (via Flyway migration V8), ensuring historical telemetry records remain attributed to their agricultural field even if the node or zone is subsequently remapped. The system SHALL provide an administrative endpoint (`PUT /api/v1/nodes/{id}/field-assignment`) to dynamically reassign a node's target field and monitoring zone.

#### Scenario: Telemetry persists immutable field attribution
- **WHEN** an uplink arrives from a node assigned to "Zone 1" belonging to "Field Alpha" (`field_id: uuid-1`)
- **THEN** the persisted `TelemetryReading` record stores `field_id = uuid-1` alongside `node_id`

#### Scenario: Dynamic node field reassignment
- **WHEN** an operator submits `PUT /api/v1/nodes/{id}/field-assignment` with a new `fieldId` and `monitoringZoneId`
- **THEN** the system updates the edge node's zone and field assignment, returns updated `EdgeNodeResponse` with `fieldId` and `fieldName`, and logs the assignment change

