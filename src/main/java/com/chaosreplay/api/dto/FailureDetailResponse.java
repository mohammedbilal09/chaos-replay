package com.chaosreplay.api.dto;

import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.Severity;
import com.chaosreplay.domain.TelemetryEvent;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

/**
 * Detailed representation of the first failure encountered within a trace execution timeline.
 */
public record FailureDetailResponse(
        String eventId,
        String serviceName,
        String serviceInstance,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp,
        EventType eventType,
        Severity severity,
        String operation,
        String message
) {

    public static FailureDetailResponse fromEntity(TelemetryEvent event) {
        if (event == null) {
            return null;
        }
        return new FailureDetailResponse(
                event.getEventId(),
                event.getServiceName(),
                event.getServiceInstance(),
                event.getTimestamp(),
                event.getEventType(),
                event.getSeverity(),
                event.getOperation(),
                event.getMessage()
        );
    }
}

