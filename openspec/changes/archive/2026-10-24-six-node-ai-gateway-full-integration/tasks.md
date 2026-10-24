# Implementation Tasks: Full Six-Node, ArvaGate, and AI Integration

## Phase 1: Edge & Firmware Updates (ArvaNode & ArvaGate)
- [x] 1.1 Preserve existing modifications in `arvacore_arvanode` and establish SX1278 LoRa protocol specification.
- [x] 1.2 Implement TDMA slotting configuration and profile provisioning for `ARVANODE-001` through `ARVANODE-006`.
- [x] 1.3 Implement resilient LoRa-to-cellular forwarding with LittleFS store-and-forward buffering in `ArvaLink/sketch_oct24a/sketch_oct24a.ino`.

## Phase 2: Backend Scalability & Ingestion (ArvaNet_Backend)
- [x] 2.1 Provision database entities, credentials, and 1:1 zone bindings for ARVANODE-001 through ARVANODE-006 via migration V21.
- [x] 2.2 Scale `TelemetryIngestionServiceImpl` for six nodes with fixed zone validation, duplicate frame suppression, and plausible sensor persistence.
- [x] 2.3 Configure Mosquitto ACLs in `mosquitto/config/acls` for gateway uplinks and downlinks.

## Phase 3: Arva AI Decision Support Service
- [x] 3.1 Scaffold `arvaAI` microservice with `requirements.txt`, environment templates, and schemas.
- [x] 3.2 Implement declarative read-only tools with zero-actuation safety guarantees in `read_only_tools.py`.
- [x] 3.3 Implement `gemini-2.5-flash` advisor service with prompt-injection defense and IRRI AWD guidelines in `gemini_advisor.py`.
- [x] 3.4 Establish container deployment specification and Caddy routing contract in documentation.

## Phase 4: ArvaLink Client Integration (ArvaNet_Frontend)
- [x] 4.1 Verify dynamic field terrace and ultrasonic diagram rendering for six nodes (`ARVANODE-001` through `006`).
- [x] 4.2 Integrate `ArvaAiAdvisorCard` into `HomeScreen` with visually separated advisory text, human review button, and safety disclaimer.

## Phase 5: Verification & Commissioning
- [x] 5.1 Execute automated cross-repository integration tests validating 6-node identities and contracts.
- [x] 5.2 Verify LittleFS flash store-and-forward queueing logic and bounded limits in `ArvaLink/tests/test_gateway_logic.py`.
- [x] 5.3 Audit AI safety invariants: verified 100% rejection of actuation requests and jailbreak attempts in `arvaAI/tests/`.
