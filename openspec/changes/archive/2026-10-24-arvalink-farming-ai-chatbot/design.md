# Design: ArvaLink Farming AI Chatbot System

## Architecture Overview

```mermaid
sequenceDiagram
    autonumber
    actor Farmer as Farmer (ArvaLink App)
    participant UI as Flutter FarmingChatbotScreen
    participant Backend as ArvaNet Backend (/api/v1/farming-chat)
    participant DB as PostgreSQL (planting_plans, tasks, sessions)
    participant AI as Arva AI Microservice (Gemini 2.5 Flash)
    participant Core as ArvaCore Decision Engine / Safety Interlocks

    Farmer->>UI: Opens Chatbot / Selects Crop
    UI->>Backend: POST /api/v1/farming-chat/message
    Backend->>DB: Load active session & planting plan
    Backend->>AI: Evaluate query + Grounded Telemetry
    AI->>Backend: Agronomic analysis + readiness + structured tasks
    Backend->>DB: Persist conversation state & plan tasks
    Backend->>UI: Formatted response + categorized task checklist
    Farmer->>UI: Clicks "Proceed with planting preparation"
    UI->>Backend: POST /api/v1/farming-chat/plans/{id}/confirm
    Backend->>Core: Validate safety interlocks (E-Stop, Dry Run, Overrides)
    Core-->>Backend: Approved / Blocked
    Backend->>UI: Action outcome with physical sensor evidence
```

## Agronomic Reference Standards
- **Lowland Rice (NSIC Rc 222)**: IRRI Alternate Wetting and Drying (AWD) protocol; safe drying to -15 cm, reflood +5 cm; continuous flooding during flowering (1-5 cm).
- **Hybrid Yellow Corn / Maize**: Soil moisture threshold 50-70% field capacity; waterlogged roots (>85% moisture for >48h) trigger aeration/drainage warnings.
- **Vegetable Crops (Eggplant, Tomato, Onion)**: Strict avoidance of saturated root zones; drip/furrow replenishment when moisture drops below 40%.

## Four-Category Task Separation Model
1. `SYSTEM_AUTOMATED`: Pre-irrigation soaking, sensor baseline calibration, scheduled water delivery (requires explicit confirmation).
2. `FARMER_MANUAL`: Land preparation, plowing/harrowing, leveling, seedbed soaking, bund repair, basal fertilizer application.
3. `EQUIPMENT_INFO`: Rotavator/tractor availability, water source salinity test, certified seed tag verification.
4. `SAFETY_BLOCKED`: Operations halted due to active Emergency Stop, pump dry-run risk, rain sensor trigger, or offline nodes.
