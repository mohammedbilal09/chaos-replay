package com.chaosreplay.api.dto;

import com.chaosreplay.domain.Severity;

import java.util.List;

/**
 * Correlated execution trace containing chronologically ordered events and trace-level summary metadata.
 */
public record TraceCorrelationResponse(
        String traceId,
        int eventCount,
        boolean hasErrors,
        Severity highestSeverity,
        List<String> services,
        List<TelemetryEventResponse> events
) {
}

