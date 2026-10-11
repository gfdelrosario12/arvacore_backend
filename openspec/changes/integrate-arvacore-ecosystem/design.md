# Design: integrate-arvacore-ecosystem

## Architecture Overview

```mermaid
flowchart TD
    subgraph Edge Nodes ["ArvaNodes (ESP32)"]
        Node1["ArvaNode 1 (JSN-SR04T + Soil)"]
        Node2["ArvaNode 2 (JSN-SR04T + Soil)"]
        Node3["ArvaNode 3 (JSN-SR04T + Soil)"]
        Node4["ArvaNode 4 (JSN-SR04T + Soil)"]
    end

    subgraph Gateway ["Field Gateway / Local Router"]
        GW["Wi-Fi / AP Gateway"]
    end

    subgraph Backend ["ArvaCore Backend Services (Spring Boot)"]
        AuthFilter["GatewayAuthenticationFilter (X-ArvaCore-Gateway-Key)"]
        IngestController["TelemetryIngestionController"]
        Decoder["JsonPayloadDecoder (Strict Validation)"]
        Validator["TelemetryValidator (Plausibility Checks)"]
        AggService["ZoneAggregationServiceImpl"]
        CommandFSM["CommandStateMachineController"]
    end

    subgraph Frontend ["ArvaNet (Flutter App)"]
        ApiClient["ApiClient (v1 Route Resolution)"]
        UI["FieldScreen & ManualControlScreen"]
    end

    Node1 -->|HTTP POST JSON| GW
    Node2 -->|HTTP POST JSON| GW
    Node3 -->|HTTP POST JSON| GW
    Node4 -->|HTTP POST JSON| GW
    GW --> AuthFilter
    AuthFilter --> IngestController
    IngestController --> Decoder
    Decoder --> Validator
    Validator --> AggService
    UI --> ApiClient
    ApiClient --> IngestController
    ApiClient --> CommandFSM
```

## Key Invariants
1. **Zero Fabrication**: When telemetry readings are missing or out of valid physical range (e.g. soil moisture < 0% or > 100%, negative distances, ultrasonic distance out of 20–450 cm range), the system rejects or flags them rather than injecting synthetic defaults.
2. **Ultrasonic Still-Well Model**: Distance reading measures sensor-to-float distance inside perforated PVC casing. Water level relative to surface is computed as:
   $$\text{Water Level (cm)} = \text{Sensor Datum Offset (20.0 cm)} - \text{Measured Distance (cm)}$$
3. **Single Point of Control**: Irrigation commands control the central field pump and distribution solenoid valve. Zone endpoints are strictly monitoring observation points.

