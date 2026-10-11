## ADDED Requirements

### Requirement: System SHALL ingest truthful field telemetry without fabricated defaults
The system SHALL ingest physical node telemetry from ArvaNodes through gateway endpoints (`POST /api/v1/telemetry/uplink/{nodeId}`), validate physical sensor bounds (ultrasonic distance 20–450 cm, soil moisture 0–100%, battery voltage 2.5–5.0V), reject implausible measurements, and never inject synthetic default sensor readings.

#### Scenario: Ingest valid physical sensor telemetry
- **WHEN** an ArvaNode transmits valid distance, water level, and soil moisture measurements with valid gateway credentials
- **THEN** the system decodes the payload, validates physical sensor plausibility, persists `TelemetryReading` entities, and returns HTTP 200

#### Scenario: Reject out-of-bounds telemetry measurements
- **WHEN** an uplink contains soil moisture < 0% or > 100%, or ultrasonic distance < 20 cm or > 450 cm
- **THEN** the system rejects the implausible measurement channel without fabricating fallback readings

### Requirement: System SHALL authenticate gateway telemetry via constant-time key validation
The system SHALL enforce gateway token authentication using the `X-ArvaCore-Gateway-Key` header on all telemetry endpoints (`/api/v1/telemetry/**`).

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
