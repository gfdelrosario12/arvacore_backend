# Proposal: Six-Node Field Deployment, ArvaGate Cellular Bridge, and Backend-Mediated Gemini AI Integration

## Why
The foundational ArvaCore system validated a 4-node Wi-Fi based prototype for Alternate Wetting and Drying (AWD) in rice cultivation. However, the physical environment of the 1.1-hectare rice farm in Barangay Silang, Lopez, Quezon features non-uniform terracing (Pitaks 1 to 6) and distance constraints that make standard 2.4GHz Wi-Fi unreliable. Additionally, caretakers require natural-language decision support grounded in live sensor data without compromising deterministic actuation safety.

## What Changes
1. **Six-Node Mesh/Star Deployment (`arvacore_arvanode`)**:
   - Provision unique profiles `ARVANODE-001` through `ARVANODE-006` with SX1278 LoRa 433MHz transmission.
   - Implement TDMA slotting (60-second cycle) to eliminate packet collisions.
2. **ArvaGate Cellular MQTT Forwarder (`ArvaGate`)**:
   - Complete `sketch_oct24a.ino` with TLS MQTT publishing to `mqtt.gladwindr.me:8883`.
   - Implement LittleFS store-and-forward flash buffering during DITO cellular outages.
3. **Backend Telemetry & Node Scaling (`ArvaNet_Backend`)**:
   - Support simultaneous 6-node telemetry ingestion, spatial weighted zone averaging, and audit logging.
4. **Arva AI Decision Support Service (`arva-ai`)**:
   - Deploy backend-mediated Python FastAPI microservice using `google-genai` SDK (`gemini-2.5-flash`).
   - Implement read-only tool calling (`get_latest_field_telemetry`, `get_irrigation_event_history`) with deterministic zero-actuation safety invariants.
5. **ArvaLink UI Presentation (`ArvaNet_Frontend`)**:
   - Add Arva AI advisory card and interactive confirmation flow for recommended irrigation actions.

## Non-Goals
- Allowing AI to directly trigger pump relays or bypass the `ArvaCoreDecisionEngine` safety interlocks.
- Overwriting existing uncommitted user changes in `ARVANODE-001.ino` and `ARVANODE-002.ino`.
- Running high-bandwidth services or video streaming over the Cat-1 cellular link.

## Impact
- **Security**: Preserves `ARVA-HMAC-V1` zero-trust authentication; API keys isolated in backend.
- **Reliability**: Zero data loss during 60-minute cellular outages via LittleFS buffer; dry-run cutoff at 15s.
- **Agronomics**: Granular spatial AWD monitoring across upper, middle, and tail drainage terraces.
