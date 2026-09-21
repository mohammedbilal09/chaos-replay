-- V1__create_telemetry_events.sql
-- ChaosReplay Phase 2: Telemetry Ingestion Schema
-- Creates the telemetry_events table, constraints, and optimized query indexes.

CREATE TABLE telemetry_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    service_name VARCHAR(100) NOT NULL,
    service_instance VARCHAR(100),
    event_type VARCHAR(32) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    trace_id VARCHAR(64),
    request_id VARCHAR(64),
    operation VARCHAR(255),
    message TEXT,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_telemetry_events_event_id UNIQUE (event_id)
);

-- ============================================================================
-- INDEX DESIGN RATIONALE
-- ============================================================================
-- 1. Unique constraint uq_telemetry_events_event_id automatically creates a B-Tree
--    index on event_id. This is essential for:
--    a) Constant-time duplicate detection at the database layer.
--    b) Concurrency protection against race conditions where multiple requests
--       attempt to ingest identical events simultaneously.

-- 2. Timestamp index:
--    Telemetry is intrinsically temporal. Future phases (timeline reconstruction,
--    incident playback, sliding window correlation) query events ordered by occurrence time.
CREATE INDEX idx_telemetry_events_timestamp ON telemetry_events (timestamp DESC);

-- 3. Composite Service + Timestamp index:
--    Supports the most common operational query: "Show recent events or errors for
--    service X over a specific time range." The composite index avoids separate index
--    intersection scans.
CREATE INDEX idx_telemetry_events_service_timestamp ON telemetry_events (service_name, timestamp DESC);

-- 4. Trace ID index:
--    Essential for Phase 3 (Event Correlation). Allows fast retrieval of all cross-service
--    telemetry events associated with a single distributed trace.
CREATE INDEX idx_telemetry_events_trace_id ON telemetry_events (trace_id);

-- 5. Request ID index:
--    Allows isolating all operations triggered by a specific ingress request.
CREATE INDEX idx_telemetry_events_request_id ON telemetry_events (request_id);

-- 6. Event Type and Severity index:
--    Accelerates filtering for specific failure patterns (e.g., eventType = 'ERROR'
--    or severity = 'FATAL') during incident detection.
CREATE INDEX idx_telemetry_events_type_severity ON telemetry_events (event_type, severity);

