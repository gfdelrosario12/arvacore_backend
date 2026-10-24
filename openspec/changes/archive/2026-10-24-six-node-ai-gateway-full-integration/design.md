# Design: Six-Node, ArvaGate, and Gemini AI Architecture

## Architecture Overview
```
[ARVANODE-001..006] (LoRa 433MHz TDMA)
       │
       ▼
[ArvaGate] (LilyGO A-7670G R2: LoRa Rx + DITO 4G LTE Cat-1 + LittleFS buffer)
       │
       ▼ (MQTT TLS 8883)
[Mosquitto Broker] ──> [ArvaNet Backend (Spring Boot)] <──> [PostgreSQL / TimescaleDB]
                              │                     │
                              ▼ (SSE / REST)        ▼ (Internal Tool Calls)
                     [ArvaLink (Flutter)]   [Arva AI (FastAPI + Gemini 2.5 Flash)]
```

## Key Technical Decisions
1. **Sub-GHz LoRa (433MHz) with TDMA**:
   - 60-second telemetry epoch divided into 6 distinct 5-second transmission slots with random jitter (\pm 500ms).
   - Eliminates single-channel LoRa collisions without requiring a multi-channel LoRaWAN gateway.
2. **Autonomous Offline Buffering**:
   - When DITO LTE network disconnects, ArvaGate writes raw packets to LittleFS (`/telemetry_buffer.log`).
   - Upon reconnect, records are flushed FIFO to MQTT.
3. **Backend-Mediated AI Safety Invariant**:
   - The AI agent is given read-only retrieval tools. No actuator tools exist in its namespace.
   - Recommendations must be confirmed by an authenticated farmer in ArvaLink before the deterministic backend dispatches an MQTT command to `CTRL-FIELD-01`.
