package com.chaosreplay.api.dto;

import com.chaosreplay.domain.ReplayScenario;
import com.chaosreplay.domain.ReplayScenarioStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.List;

/**
 * Outbound DTO representation of a deterministic failure replay scenario.
 */
public record ReplayScenarioResponse(
        String scenarioId,
        String sourceTraceId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant createdAt,
        int eventCount,
        int failureCount,
        long durationMs,
        ReplayScenarioStatus status,
        List<ReplayScenarioEventResponse> events
) {

    public static ReplayScenarioResponse fromEntity(ReplayScenario scenario, List<ReplayScenarioEventResponse> events) {
        return new ReplayScenarioResponse(
                scenario.getScenarioId(),
                scenario.getSourceTraceId(),
                scenario.getCreatedAt(),
                scenario.getEventCount(),
                scenario.getFailureCount(),
                scenario.getDurationMs(),
                scenario.getStatus(),
                events
        );
    }
}

