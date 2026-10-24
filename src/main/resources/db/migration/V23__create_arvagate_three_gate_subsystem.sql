-- =============================================================================
-- Migration V23: ArvaGate Three-Gate Irrigation Subsystem
-- =============================================================================

CREATE TABLE IF NOT EXISTS arva_gate_devices (
    device_id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL,
    field_id BIGINT NOT NULL DEFAULT 1,
    firmware_version VARCHAR(32) NOT NULL DEFAULT '1.0.0',
    connectivity_status VARCHAR(32) NOT NULL DEFAULT 'ONLINE',
    last_seen_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS arva_gates (
    id SERIAL PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL REFERENCES arva_gate_devices(device_id) ON DELETE CASCADE,
    gate_index INT NOT NULL CHECK (gate_index IN (1, 2, 3)),
    gate_label VARCHAR(64) NOT NULL,
    current_state VARCHAR(32) NOT NULL DEFAULT 'CLOSED', -- OPEN, CLOSED, OPENING, CLOSING, STOPPED, FAULT
    requested_state VARCHAR(32) NOT NULL DEFAULT 'CLOSED',
    physical_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    last_actuated_at TIMESTAMP WITH TIME ZONE,
    fault_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_gate_device_index UNIQUE (device_id, gate_index)
);

CREATE TABLE IF NOT EXISTS arva_gate_commands (
    command_id VARCHAR(64) PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL REFERENCES arva_gate_devices(device_id) ON DELETE CASCADE,
    target_gates VARCHAR(64) NOT NULL, -- e.g. "1", "2", "3", "1,2", "ALL"
    action VARCHAR(32) NOT NULL, -- OPEN, CLOSE, STOP
    duration_seconds INT DEFAULT 0,
    issued_by VARCHAR(64) NOT NULL, -- operator / user / system
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- PENDING, ACCEPTED, EXECUTED, REJECTED, FAILED
    error_details TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    executed_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS arva_gate_events (
    id BIGSERIAL PRIMARY KEY,
    command_id VARCHAR(64),
    device_id VARCHAR(64) NOT NULL,
    gate_index INT CHECK (gate_index IN (1, 2, 3)),
    event_type VARCHAR(64) NOT NULL,
    previous_state VARCHAR(32),
    new_state VARCHAR(32),
    details TEXT,
    actor VARCHAR(64) NOT NULL DEFAULT 'SYSTEM',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_arva_gate_events_device ON arva_gate_events(device_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_arva_gate_commands_status ON arva_gate_commands(device_id, status);

-- Seed default ArvaGate device and 3 physical gates
INSERT INTO arva_gate_devices (device_id, display_name, field_id, firmware_version, connectivity_status)
SELECT 'ARVAGATE-CTRL-01', 'ArvaGate Central Canal Controller', 1, '1.0.0', 'ONLINE'
WHERE NOT EXISTS (SELECT 1 FROM arva_gate_devices WHERE device_id = 'ARVAGATE-CTRL-01');

INSERT INTO arva_gates (device_id, gate_index, gate_label, current_state, requested_state, physical_confirmed)
SELECT 'ARVAGATE-CTRL-01', 1, 'Gate 1 (Inflow Canal / Pitak 1-2)', 'CLOSED', 'CLOSED', FALSE
WHERE NOT EXISTS (SELECT 1 FROM arva_gates WHERE device_id = 'ARVAGATE-CTRL-01' AND gate_index = 1);

INSERT INTO arva_gates (device_id, gate_index, gate_label, current_state, requested_state, physical_confirmed)
SELECT 'ARVAGATE-CTRL-01', 2, 'Gate 2 (Mid-Terrace Lateral / Pitak 3-4)', 'CLOSED', 'CLOSED', FALSE
WHERE NOT EXISTS (SELECT 1 FROM arva_gates WHERE device_id = 'ARVAGATE-CTRL-01' AND gate_index = 2);

INSERT INTO arva_gates (device_id, gate_index, gate_label, current_state, requested_state, physical_confirmed)
SELECT 'ARVAGATE-CTRL-01', 3, 'Gate 3 (Distal Distribution / Pitak 5-6)', 'CLOSED', 'CLOSED', FALSE
WHERE NOT EXISTS (SELECT 1 FROM arva_gates WHERE device_id = 'ARVAGATE-CTRL-01' AND gate_index = 3);
