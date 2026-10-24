-- Migration V22: Farming AI Chatbot Sessions, Planting Plans, and Preparation Tasks

CREATE TABLE chatbot_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL UNIQUE,
    user_id VARCHAR(100) NOT NULL,
    field_id BIGINT REFERENCES fields(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    current_step VARCHAR(64) NOT NULL DEFAULT 'CROP_SELECTION',
    selected_crop VARCHAR(100),
    planting_method VARCHAR(100),
    intended_schedule VARCHAR(255),
    metadata_json TEXT
);

CREATE INDEX idx_chatbot_sessions_user ON chatbot_sessions(user_id);
CREATE INDEX idx_chatbot_sessions_field ON chatbot_sessions(field_id);

CREATE TABLE chatbot_messages (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL REFERENCES chatbot_sessions(session_id) ON DELETE CASCADE,
    sender VARCHAR(20) NOT NULL, -- 'USER', 'ASSISTANT', 'SYSTEM'
    message_text TEXT NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    structured_payload_json TEXT
);

CREATE INDEX idx_chatbot_messages_session ON chatbot_messages(session_id);

CREATE TABLE planting_plans (
    id BIGSERIAL PRIMARY KEY,
    plan_code VARCHAR(64) NOT NULL UNIQUE,
    session_id VARCHAR(64) REFERENCES chatbot_sessions(session_id) ON DELETE SET NULL,
    field_id BIGINT REFERENCES fields(id) ON DELETE CASCADE NOT NULL,
    crop_name VARCHAR(100) NOT NULL,
    planting_method VARCHAR(100),
    intended_schedule VARCHAR(255),
    readiness_score INTEGER NOT NULL DEFAULT 0, -- 0 to 100
    readiness_status VARCHAR(50) NOT NULL DEFAULT 'ASSESSING', -- ASSESSING, READY, BLOCKED, PREPARING, COMPLETED
    summary_text TEXT,
    irrigation_safety_status VARCHAR(50) NOT NULL DEFAULT 'VERIFIED_SAFE',
    farmer_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    farmer_confirmed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_planting_plans_field ON planting_plans(field_id);

CREATE TABLE planting_tasks (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT REFERENCES planting_plans(id) ON DELETE CASCADE NOT NULL,
    category VARCHAR(50) NOT NULL, -- 'SYSTEM_AUTOMATED', 'FARMER_MANUAL', 'EQUIPMENT_INFO', 'SAFETY_BLOCKED'
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, IN_PROGRESS, COMPLETED, BLOCKED
    blocking_reason VARCHAR(255),
    requires_confirmation BOOLEAN NOT NULL DEFAULT FALSE,
    executed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_planting_tasks_plan ON planting_tasks(plan_id);

