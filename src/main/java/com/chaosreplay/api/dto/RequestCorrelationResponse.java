package com.chaosreplay.api.dto;

import com.chaosreplay.domain.Severity;

import java.util.List;

/**
 * Correlated ingress request containing chronologically ordered events and request-level summary metadata.
 */
public record RequestCorrelationResponse(
        String requestId,
        int eventCount,
        boolean hasErrors,
        Severity highestSeverity,
        List<String> services,
        List<TelemetryEventResponse> events
) {
}

