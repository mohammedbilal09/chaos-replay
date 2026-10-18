-- V5__performance_indexes.sql
-- ChaosReplay Phase 9: Performance Optimization & Query Composite Indexes
-- Adds composite indexes to eliminate sorting overhead and accelerate top-1 queries.

-- 1. Composite index for deterministic trace event retrieval ordered by timestamp ASC, event_id ASC.
--    Eliminates explicit SORT node in PostgreSQL query execution plans.
CREATE INDEX idx_telemetry_events_trace_timestamp_event
    ON telemetry_events (trace_id, timestamp ASC, event_id ASC);

-- 2. Composite index for deterministic request event retrieval ordered by timestamp ASC, event_id ASC.
CREATE INDEX idx_telemetry_events_request_timestamp_event
    ON telemetry_events (request_id, timestamp ASC, event_id ASC);

-- 3. Composite index for latest replay scenario retrieval by source trace ID.
--    Supports findFirstBySourceTraceIdOrderByCreatedAtDesc without sorting.
CREATE INDEX idx_replay_scenarios_trace_created
    ON replay_scenarios (source_trace_id, created_at DESC);

-- 4. Composite index for latest replay verification retrieval by scenario ID.
--    Supports findFirstByScenarioIdOrderByCreatedAtDesc without sorting.
CREATE INDEX idx_replay_verifications_scenario_created
    ON replay_verifications (scenario_id, created_at DESC);

-- 5. Composite index for latest failure analysis retrieval by trace ID.
--    Supports findFirstByTraceIdOrderByCreatedAtDesc without sorting.
CREATE INDEX idx_failure_analyses_trace_created
    ON failure_analyses (trace_id, created_at DESC);

