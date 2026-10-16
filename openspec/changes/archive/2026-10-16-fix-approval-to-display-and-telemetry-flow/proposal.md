# OpenSpec Proposal: Fix Approval-to-Display and Telemetry Ingestion Flow

## 1. Problem Statement
In the ArvaCore IoT ecosystem, nodes were successfully pairing and receiving approval, but approved nodes failed to appear in the Fields section and telemetry was absent from the Home dashboard.
Investigation identified four root causes in the backend:
1. `EdgeNodeResponse` serialized monitoring zone as `monitoringZoneId`, while client mapping expected `assignedZoneId`.
2. `ZoneServiceImpl.getAllZones()` read from the unpopulated legacy `zones` table rather than falling back to the seeded and active `monitoring_zones`.
3. `TelemetryUplinkRequest` lacked top-level deserialization for `soilMoisturePercent`, `batteryVoltage`, and `seq` as transmitted by the hardware ESP32 firmware.
4. `ZoneAggregationServiceImpl` filtered solely on `SensorType.WATER_LEVEL` instead of `SensorType.WATER_LEVEL_DISTANCE` from ultrasonic sensors (JSN-SR04T), resulting in null aggregated water levels.

## 2. Proposed Changes
- Update `TelemetryUplinkRequest` with `@JsonIgnoreProperties(ignoreUnknown = true)` and fields for `batteryVoltage`, `soilMoisturePercent`, and `seq`.
- Map top-level `soilMoisturePercent` and `batteryVoltage` in `JsonPayloadDecoder` into measurement maps.
- In `ZoneAggregationServiceImpl`, calculate pond water depth from ultrasonic distance readings using the still-well geometry datum: $h = \max(0.0, 30.0 - d)$.
- Add live telemetry fields (`soilMoisturePercent`, `waterLevelCm`, `batteryVoltage`) to `EdgeNodeResponse` and map them via `DtoMapper`.
- Update `ZoneServiceImpl` to fall back to `monitoringZoneRepository` when `zoneRepository` has no records, enriching responses with aggregated live telemetry and assigned node IDs.

## 3. Verification & Compliance
- Ensure all 222 backend unit and integration tests pass cleanly (`./mvnw test`).
- Ensure no unsupported sensors (e.g. ambient temperature or humidity) are fabricated or simulated.

