package com.chaosreplay.api.dto;

import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.Severity;
import com.chaosreplay.domain.TelemetryEvent;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.Map;

/**
 * Outbound response representation of a stored telemetry event.
 */
public record TelemetryEventResponse(
        Long id,
        String eventId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp,
        String serviceName,
        String serviceInstance,
        EventType eventType,
        Severity severity,
        String traceId,
        String requestId,
        String operation,
        String message,
        Map<String, Object> metadata,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant createdAt
) {

    public static TelemetryEventResponse fromEntity(TelemetryEvent event) {
        return new TelemetryEventResponse(
                event.getId(),
                event.getEventId(),
                event.getTimestamp(),
                event.getServiceName(),
                event.getServiceInstance(),
                event.getEventType(),
                event.getSeverity(),
                event.getTraceId(),
                event.getRequestId(),
                event.getOperation(),
                event.getMessage(),
                event.getMetadata(),
                event.getCreatedAt()
        );
    }
}

