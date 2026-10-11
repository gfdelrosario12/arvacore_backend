# Tasks: integrate-arvacore-ecosystem

- [x] Review thesis research paper `ARVACORE.pdf` and extract physical node topology, sensor pinouts, AWD thresholds, and pump verification requirements
- [x] Add `WATER_LEVEL_DISTANCE` sensor type and unit (`CENTIMETERS`) to backend schema
- [x] Eliminate fabricated telemetry defaults in `JsonPayloadDecoder` and implement `TelemetryValidator`
- [x] Fix water level vs water flow calculation bug in `ZoneAggregationServiceImpl`
- [x] Implement secure constant-time gateway authentication (`X-ArvaCore-Gateway-Key`) on `/api/v1/telemetry/**`
- [x] Rebrand OpenAPI title to "ArvaCore Backend Services API"
- [x] Author comprehensive backend integrity unit and integration tests (`ArvaCoreTelemetryIntegrityTest`)
- [x] Upgrade ESP32 firmware (`sketch_oct11a.ino`) with Wi-Fi connectivity, JSON payload packaging, and authenticated uplink
- [x] Create uplink gateway test script (`test_uplink_gateway.py`)
- [x] Update frontend `ApiConfig` with `ARVACORE_API_BASE_URL` and `ARVACORE_ALLOW_INSECURE_HTTP`
- [x] Update `ApiClient._buildUri` with `/api/v1` route prefix and quarters-to-zones mapping
- [x] Fix frontend analyzer warnings (dead code, duplicate imports, redundant null checks)
- [x] Rebrand frontend app title and subtitle to "ArvaCore ArvaNet"
- [x] Run full test suites: Spring Boot Maven (187 passing tests) and Flutter Dart analyzer (0 errors, 0 warnings)

