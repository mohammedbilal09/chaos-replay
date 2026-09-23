package com.chaosreplay.api.dto;

import com.chaosreplay.domain.ReplayScenarioStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

/**
 * Outbound DTO representation of the result of a deterministic simulation replay execution.
 */
public record ReplayExecutionResponse(
        String scenarioId,
        ReplayScenarioStatus status,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant startedAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant completedAt,
        long durationMs,
        int eventsProcessed,
        int failuresSimulated,
        String message
) {
}

