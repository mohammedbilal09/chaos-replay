-- V4__create_failure_analyses.sql
-- ChaosReplay Phase 6: Deterministic Failure Analysis & Root-Cause Evidence Schema
-- Creates failure_analyses, failure_analysis_candidates, and failure_analysis_evidence tables.

CREATE TABLE failure_analyses (
    id BIGSERIAL PRIMARY KEY,
    analysis_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    scenario_id VARCHAR(64),
    verification_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    conclusion VARCHAR(64) NOT NULL,
    confidence_score DECIMAL(5,4) NOT NULL,
    event_count INT NOT NULL,
    failure_count INT NOT NULL,
    candidate_count INT NOT NULL,
    primary_service_name VARCHAR(100),
    primary_event_id VARCHAR(64),
    summary TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_failure_analyses_analysis_id UNIQUE (analysis_id),
    CONSTRAINT chk_failure_analyses_confidence CHECK (confidence_score >= 0 AND confidence_score <= 1),
    CONSTRAINT chk_failure_analyses_event_count CHECK (event_count >= 0),
    CONSTRAINT chk_failure_analyses_failure_count CHECK (failure_count >= 0),
    CONSTRAINT chk_failure_analyses_candidate_count CHECK (candidate_count >= 0)
);

CREATE INDEX idx_failure_analyses_trace_id ON failure_analyses (trace_id);
CREATE INDEX idx_failure_analyses_scenario_id ON failure_analyses (scenario_id);
CREATE INDEX idx_failure_analyses_verification_id ON failure_analyses (verification_id);
CREATE INDEX idx_failure_analyses_created_at ON failure_analyses (created_at DESC);

CREATE TABLE failure_analysis_candidates (
    id BIGSERIAL PRIMARY KEY,
    analysis_id VARCHAR(64) NOT NULL,
    rank INT NOT NULL,
    service_name VARCHAR(100),
    event_id VARCHAR(64),
    event_type VARCHAR(32),
    severity VARCHAR(20),
    candidate_type VARCHAR(64) NOT NULL,
    confidence_score DECIMAL(5,4) NOT NULL,
    first_observed_at TIMESTAMPTZ,
    description TEXT NOT NULL,

    CONSTRAINT fk_failure_analysis_candidates_analysis_id
        FOREIGN KEY (analysis_id) REFERENCES failure_analyses (analysis_id) ON DELETE CASCADE,
    CONSTRAINT uq_failure_analysis_candidates_rank UNIQUE (analysis_id, rank),
    CONSTRAINT chk_failure_analysis_candidates_confidence CHECK (confidence_score >= 0 AND confidence_score <= 1)
);

CREATE INDEX idx_failure_analysis_candidates_analysis_id ON failure_analysis_candidates (analysis_id);
CREATE INDEX idx_failure_analysis_candidates_event_id ON failure_analysis_candidates (event_id);
CREATE INDEX idx_failure_analysis_candidates_service_name ON failure_analysis_candidates (service_name);

CREATE TABLE failure_analysis_evidence (
    id BIGSERIAL PRIMARY KEY,
    analysis_id VARCHAR(64) NOT NULL,
    candidate_rank INT NOT NULL,
    sequence_number INT,
    event_id VARCHAR(64),
    evidence_type VARCHAR(64) NOT NULL,
    evidence_value TEXT,

    CONSTRAINT fk_failure_analysis_evidence_analysis_id
        FOREIGN KEY (analysis_id) REFERENCES failure_analyses (analysis_id) ON DELETE CASCADE
);

CREATE INDEX idx_failure_analysis_evidence_analysis_candidate ON failure_analysis_evidence (analysis_id, candidate_rank);

