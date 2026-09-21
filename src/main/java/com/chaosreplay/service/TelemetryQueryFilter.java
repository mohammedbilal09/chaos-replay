package com.chaosreplay.service;

import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.Severity;

import java.time.Instant;

/**
 * Encapsulates optional search criteria for querying telemetry events.
 */
public record TelemetryQueryFilter(
        String serviceName,
        EventType eventType,
        Severity severity,
        String traceId,
        String requestId,
        Instant from,
        Instant to
) {

    /**
     * Validates temporal consistency of the filter boundaries.
     *
     * @throws IllegalArgumentException if 'from' is strictly after 'to'
     */
    public void validate() {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(
                    String.format("The 'from' timestamp [%s] must not be after the 'to' timestamp [%s]", from, to)
            );
        }
    }
}

