# Tasks: Fix Approval-to-Display and Telemetry Ingestion Flow

- [x] Update `TelemetryUplinkRequest` with `batteryVoltage`, `soilMoisturePercent`, `seq`, and `@JsonIgnoreProperties(ignoreUnknown = true)`
- [x] Normalize payload keys in `JsonPayloadDecoder` for soil moisture, ultrasonic distance, and battery voltage
- [x] Enhance `ZoneAggregationServiceImpl` to convert ultrasonic `WATER_LEVEL_DISTANCE` into pond water depth
- [x] Extend `EdgeNodeResponse` and `DtoMapper` with live readings (`soilMoisturePercent`, `waterLevelCm`, `batteryVoltage`)
- [x] Extend `ZoneResponse` and `ZoneServiceImpl` with fallback to `MonitoringZone` enriched with live telemetry and assigned node IDs
- [x] Run full backend test suite (`./mvnw test`) and verify 222 tests pass with 0 failures

