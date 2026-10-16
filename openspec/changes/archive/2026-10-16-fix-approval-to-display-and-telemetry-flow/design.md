# Design: Approval-to-Display & Ingestion Integration

## Architecture & Data Flow
1. **Telemetry Uplink Ingestion**:
   - ESP32 posts JSON with `water_level_distance`, `soilMoisturePercent`, `batteryVoltage`, and `seq`.
   - `TelemetryUplinkRequest` deserializes top-level and measurements map seamlessly.
   - `JsonPayloadDecoder` normalizes these into `SensorType.SOIL_MOISTURE`, `SensorType.WATER_LEVEL_DISTANCE`, and `SensorType.BATTERY_VOLTAGE`.
2. **Zone Aggregation**:
   - `ZoneAggregationServiceImpl` processes both direct water level and ultrasonic distance measurements.
   - Pond depth is derived from ultrasonic distance:
     $$\text{depth}_{\text{cm}} = \max(0.0, 30.0 - \text{distance}_{\text{cm}})$$
3. **Edge Node & Zone Enrichment**:
   - `EdgeNodeRegistryServiceImpl` and `DtoMapper` enrich node DTOs with their latest telemetry readings.
   - `ZoneServiceImpl` dynamically resolves monitoring zones, aggregates live node readings, and populates `assignedNodeIds`.
