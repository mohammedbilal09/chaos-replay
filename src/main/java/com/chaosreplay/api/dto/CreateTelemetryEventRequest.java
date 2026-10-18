package com.chaosreplay.api.dto;

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
 * Inbound request payload for ingesting a single distributed telemetry event.
 */
public record CreateTelemetryEventRequest(
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
}
