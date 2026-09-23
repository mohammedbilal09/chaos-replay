-- V2__create_replay_scenarios.sql
-- ChaosReplay Phase 4: Deterministic Failure Replay & Scenario Generation Schema
-- Creates replay_scenarios and replay_scenario_events tables with constraints and indexes.

CREATE TABLE replay_scenarios (
    id BIGSERIAL PRIMARY KEY,
    scenario_id VARCHAR(64) NOT NULL,
    source_trace_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    event_count INT NOT NULL,
    failure_count INT NOT NULL,
    duration_ms BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,

    CONSTRAINT uq_replay_scenarios_scenario_id UNIQUE (scenario_id)
);

CREATE INDEX idx_replay_scenarios_source_trace_id ON replay_scenarios (source_trace_id);

CREATE TABLE replay_scenario_events (
    id BIGSERIAL PRIMARY KEY,
    scenario_id VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    offset_ms BIGINT NOT NULL,
    sequence_number INT NOT NULL,
    service_name VARCHAR(100) NOT NULL,
    service_instance VARCHAR(100),
    event_type VARCHAR(32) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    trace_id VARCHAR(64),
    request_id VARCHAR(64),
    operation VARCHAR(255),
    message TEXT,
    metadata JSONB,

    CONSTRAINT fk_replay_scenario_events_scenario_id
        FOREIGN KEY (scenario_id) REFERENCES replay_scenarios (scenario_id) ON DELETE CASCADE,
    CONSTRAINT uq_replay_scenario_events_scenario_seq
        UNIQUE (scenario_id, sequence_number)
);

CREATE INDEX idx_replay_scenario_events_scenario_seq ON replay_scenario_events (scenario_id, sequence_number);

