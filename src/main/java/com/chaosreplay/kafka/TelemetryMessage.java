package com.chaosreplay.kafka;

import com.chaosreplay.api.dto.CreateTelemetryEventRequest;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.Severity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

/**
 * Stable, versionable message contract for Kafka-ingested telemetry events.
 * Encapsulates distributed event identity, timestamps, tracing identifiers, and metadata.
 */
public record TelemetryMessage(
        @NotBlank(message = "eventId must not be blank")
        @Size(max = 64, message = "eventId must not exceed 64 characters")
        @Pattern(regexp = "^$|^[a-zA-Z0-9-_.:]+$", message = "eventId contains invalid characters")
        String eventId,

        @NotNull(message = "timestamp must not be null")
        Instant timestamp,

        @NotBlank(message = "serviceName must not be blank")
        @Size(max = 100, message = "serviceName must not exceed 100 characters")
        String serviceName,

        @Size(max = 100, message = "serviceInstance must not exceed 100 characters")
        String serviceInstance,

        @NotNull(message = "eventType must not be null")
        EventType eventType,

        @NotNull(message = "severity must not be null")
        Severity severity,

        @Size(max = 64, message = "traceId must not exceed 64 characters")
        @Pattern(regexp = "^$|^[a-zA-Z0-9-_.:]+$", message = "traceId contains invalid characters")
        String traceId,

        @Size(max = 64, message = "requestId must not exceed 64 characters")
        @Pattern(regexp = "^$|^[a-zA-Z0-9-_.:]+$", message = "requestId contains invalid characters")
        String requestId,

        @Size(max = 255, message = "operation must not exceed 255 characters")
        String operation,

        @Size(max = 4000, message = "message must not exceed 4000 characters")
        String message,

        @Size(max = 50, message = "metadata must not contain more than 50 entries")
        Map<String, Object> metadata
) {

    @JsonIgnore
    @AssertTrue(message = "metadata keys must be non-blank and at most 64 characters")
    public boolean isMetadataKeysValid() {
        if (metadata == null) {
            return true;
        }
        for (String key : metadata.keySet()) {
            if (key == null || key.isBlank() || key.length() > 64) {
                return false;
            }
        }
        return true;
    }

    /**
     * Converts this Kafka message to the standard internal telemetry ingestion request.
     *
     * @return equivalent {@link CreateTelemetryEventRequest}
     */
    public CreateTelemetryEventRequest toCreateRequest() {
        return new CreateTelemetryEventRequest(
                eventId,
                timestamp,
                serviceName,
                serviceInstance,
                eventType,
                severity,
                traceId,
                requestId,
                operation,
                message,
                metadata
        );
    }

    /**
     * Factory method creating a {@link TelemetryMessage} from an existing {@link CreateTelemetryEventRequest}.
     *
     * @param request source request
     * @return equivalent {@link TelemetryMessage}
     */
    public static TelemetryMessage fromCreateRequest(CreateTelemetryEventRequest request) {
        return new TelemetryMessage(
                request.eventId(),
                request.timestamp(),
                request.serviceName(),
                request.serviceInstance(),
                request.eventType(),
                request.severity(),
                request.traceId(),
                request.requestId(),
                request.operation(),
                request.message(),
                request.metadata()
        );
    }
}
