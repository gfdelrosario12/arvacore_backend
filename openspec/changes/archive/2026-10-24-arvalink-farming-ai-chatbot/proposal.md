# Change: ArvaLink Farming AI Chatbot Integration

## Why
Farmers need an intelligent, conversational agricultural companion directly within ArvaLink to guide them from crop selection to farm-readiness assessment, planting preparation, irrigation scheduling, and execution tracking, while strictly preserving zero-trust irrigation safety rules.

## What Changes
1. **ArvaAI Farming Chatbot Engine**:
   - Conversational state machine initiating with *"What do you want to plant today?"*.
   - Structured crop agronomic profile catalog (Lowland Rice NSIC Rc 222, Maize/Corn, Eggplant, Onion, Tomato) grounded in Philippine DA-PhilRice / IRRI agronomic standards.
   - 10-step guided workflow: Crop selection -> Field & Method selection -> Grounded telemetry & water retrieval -> Agronomic readiness scoring -> Preparation plan synthesis -> Explicit farmer confirmation prompt (*"Do you want to proceed with the proposed planting preparation?"*) -> Task separation (Automated vs Farmer-Required vs Equipment/Info vs Safety Blocked) -> Authorized execution via ArvaCore -> Physical evidence verification -> Progress tracking.
   - Plan persistence and conversation session management.
2. **ArvaNet Backend API & Persistence**:
   - Flyway migration `V22__farming_ai_chatbot_plans.sql` adding `planting_plans`, `planting_tasks`, and `chatbot_sessions` tables.
   - REST endpoints `/api/v1/farming-chat/message`, `/api/v1/farming-chat/sessions/{id}`, `/api/v1/farming-chat/plans/{id}/confirm`, and `/api/v1/farming-chat/plans/{id}/tasks/{taskId}/toggle`.
   - Read-only proxy to ArvaAI or integrated fallback engine ensuring zero relay actuation by AI.
3. **ArvaLink Flutter UI**:
   - `FarmingChatbotScreen` accessible from bottom navigation bar or quick action.
   - Interactive chat bubbles, crop picker, readiness score indicators, and actionable multi-category task checklists (Automated, Farmer manual, Equipment required, Safety blocked).
   - "Do you want to proceed..." confirmation dialogs wired to authorized backend control APIs.
4. **Safety & Security**:
   - AI cannot trigger pumps/valves directly.
   - Operations require deterministic checks and explicit operator confirmation.
