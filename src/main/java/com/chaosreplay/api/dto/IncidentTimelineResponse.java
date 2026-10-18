package com.chaosreplay.api.dto;

import com.chaosreplay.domain.FailureAnalysisConclusion;
import com.chaosreplay.domain.FailureAnalysisStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Immutable DTO representing a reconstructed chronological incident timeline.
 */
public record IncidentTimelineResponse(
        String traceId,
        int eventCount,
        int failureCount,
        boolean failureObserved,
        Instant firstEventAt,
        Instant lastEventAt,
        long durationMs,
        boolean analysisAvailable,
        boolean verificationAvailable,
        FailureAnalysisStatus rootCauseStatus,
        FailureAnalysisConclusion conclusion,
        String primaryServiceName,
        String primaryEventId,
        BigDecimal confidenceScore,
        List<IncidentTimelineEntryResponse> timeline,
        Instant generatedAt
) {
    public IncidentTimelineResponse {
        timeline = timeline != null ? List.copyOf(timeline) : List.of();
    }
}

