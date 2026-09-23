-- V3__create_replay_verifications.sql
-- ChaosReplay Phase 5: Replay Verification & Failure Comparison Schema
-- Creates replay_verifications and replay_verification_differences tables with constraints and indexes.

CREATE TABLE replay_verifications (
    id BIGSERIAL PRIMARY KEY,
    verification_id VARCHAR(64) NOT NULL,
    scenario_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    original_event_count INT NOT NULL,
    replayed_event_count INT NOT NULL,
    original_failure_count INT NOT NULL,
    replayed_failure_count INT NOT NULL,
    original_duration_ms BIGINT NOT NULL,
    replayed_duration_ms BIGINT NOT NULL,
    events_matched INT NOT NULL,
    events_missing INT NOT NULL,
    events_unexpected INT NOT NULL,
    severity_mismatches INT NOT NULL,
    event_type_mismatches INT NOT NULL,
    service_mismatches INT NOT NULL,
    result_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_replay_verifications_verification_id UNIQUE (verification_id),
    CONSTRAINT fk_replay_verifications_scenario_id
        FOREIGN KEY (scenario_id) REFERENCES replay_scenarios (scenario_id) ON DELETE CASCADE
);

CREATE INDEX idx_replay_verifications_scenario_id ON replay_verifications (scenario_id);
CREATE INDEX idx_replay_verifications_created_at ON replay_verifications (created_at DESC);

CREATE TABLE replay_verification_differences (
    id BIGSERIAL PRIMARY KEY,
    verification_id VARCHAR(64) NOT NULL,
    sequence_number INT NOT NULL,
    difference_type VARCHAR(32) NOT NULL,
    expected_event_id VARCHAR(64),
    actual_event_id VARCHAR(64),
    expected_service_name VARCHAR(100),
    actual_service_name VARCHAR(100),
    expected_event_type VARCHAR(32),
    actual_event_type VARCHAR(32),
    expected_severity VARCHAR(20),
    actual_severity VARCHAR(20),
    message TEXT,

    CONSTRAINT fk_replay_verification_diff_verification_id
        FOREIGN KEY (verification_id) REFERENCES replay_verifications (verification_id) ON DELETE CASCADE
);

CREATE INDEX idx_replay_verification_diff_seq ON replay_verification_differences (verification_id, sequence_number);

