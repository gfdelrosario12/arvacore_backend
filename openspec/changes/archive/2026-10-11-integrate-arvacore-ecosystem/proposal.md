# Proposal: ArvaCore Ecosystem Full-Stack & IoT Integration

## 1. Problem Statement
The ArvaCore ecosystem (Alternate Wetting and Drying (AWD) Smart Irrigation Research project for BS Computer Engineering at PUP Manila) comprises four physical/software components:
1. **ArvaNodes**: Distributed physical sensor nodes (ESP32, JSN-SR04T ultrasonic still-well water level sensor, capacitive soil moisture sensor, DHT22 temp/humidity).
2. **Central Irrigation Pump Subsystem**: Centralized 12V submersible pump, solenoid valve, and hall-effect flow sensor for verification of delivered water volume.
3. **ArvaCore Backend Services**: Spring Boot microservice orchestrating telemetry ingestion, AWD threshold evaluations, device status tracking, and command state machine.
4. **ArvaNet**: Flutter-based mobile & desktop client for field operators and administrators.

Prior to this integration, the system suffered from disconnected contracts:
- Telemetry ingestion injected fake/fabricated defaults (e.g. 35% soil moisture, 100% battery) when fields were missing.
- Backend enums lacked support for raw ultrasonic distance (`WATER_LEVEL_DISTANCE`), and zone aggregation mistakenly calculated water flow as water level.
- Physical node firmware was a standalone distance print loop without network uplink or gateway authentication.
- Frontend was pointing to legacy mock endpoints with inconsistent route prefixes (`/api` vs `/api/v1`) and mock repositories.

## 2. Solution Summary
- **Truthful Telemetry Processing**: Replaced fallback injection in `JsonPayloadDecoder` with explicit range validation (`TelemetryValidator`), preventing fabricated field conditions.
- **Physical Node Firmware & Gateway Auth**: Integrated Wi-Fi and HTTP JSON uplink with secure constant-time gateway token validation (`X-ArvaCore-Gateway-Key`) on `/api/v1/telemetry/uplink/{nodeId}`.
- **Backend Schema & Aggregation Corrections**: Added `SensorType.WATER_LEVEL_DISTANCE` (cm) and segregated ultrasonic water level distance from pipeline flow rate in `ZoneAggregationServiceImpl`.
- **Frontend Contract Alignment**: Updated `ApiClient._buildUri` to map `/api` to `/api/v1` routes and quarters to zones, updated `ApiConfig` with `ARVACORE_API_BASE_URL`, cleaned up unused/dead code, and updated UI branding to ArvaCore / ArvaNet.
- **Automated Verification**: Added comprehensive unit and integration tests for telemetry validation, non-fabrication guarantees, gateway authentication, and OpenAPI contracts.

