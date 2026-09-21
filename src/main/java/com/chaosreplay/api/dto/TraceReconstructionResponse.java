package com.chaosreplay.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.List;

/**
 * Reconstructed execution timeline, duration metrics, and failure analysis for a distributed trace.
 */
public record TraceReconstructionResponse(
        String traceId,
        int eventCount,
        List<String> services,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant startTime,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant endTime,
        long durationMs,
        boolean hasFailure,
        int failureCount,
        FailureDetailResponse firstFailure,
        List<TelemetryEventResponse> timeline
) {
}

