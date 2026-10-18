package com.chaosreplay.api.dto;

import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.IncidentEntryCategory;
import com.chaosreplay.domain.Severity;

import java.time.Instant;
import java.util.List;

/**
 * Immutable DTO representing an enriched timeline entry for incident investigation.
 */
public record IncidentTimelineEntryResponse(
        int sequenceNumber,
        String eventId,
        Instant timestamp,
        long offsetMs,
        String serviceName,
        String serviceInstance,
        EventType eventType,
        Severity severity,
        String operation,
        String message,
        IncidentEntryCategory entryCategory,
        boolean failureObserved,
        boolean failurePropagation,
        Integer relatedCandidateRank,
        List<String> evidenceReferences
) {
    public IncidentTimelineEntryResponse {
        evidenceReferences = evidenceReferences != null ? List.copyOf(evidenceReferences) : List.of();
    }
}

