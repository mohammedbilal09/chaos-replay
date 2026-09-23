package com.chaosreplay.api.dto;

import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.ReplayScenarioEvent;
import com.chaosreplay.domain.Severity;

import java.util.Map;

/**
 * Outbound DTO representation of an individual ordered event snapshot in a replay scenario.
 */
public record ReplayScenarioEventResponse(
        int sequenceNumber,
        String eventId,
        long offsetMs,
        String serviceName,
        String serviceInstance,
        EventType eventType,
        Severity severity,
        String traceId,
        String requestId,
        String operation,
        String message,
        Map<String, Object> metadata
) {

    public static ReplayScenarioEventResponse fromEntity(ReplayScenarioEvent event) {
        return new ReplayScenarioEventResponse(
                event.getSequenceNumber(),
                event.getEventId(),
                event.getOffsetMs(),
                event.getServiceName(),
                event.getServiceInstance(),
                event.getEventType(),
                event.getSeverity(),
                event.getTraceId(),
                event.getRequestId(),
                event.getOperation(),
                event.getMessage(),
                event.getMetadata()
        );
    }
}

