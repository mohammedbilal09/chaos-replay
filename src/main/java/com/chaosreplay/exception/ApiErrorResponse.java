package com.chaosreplay.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Standardized API error response payload returned across all endpoints.
 * Provides backwards-compatible basic error representation alongside structured
 * validation details and correlation identifiers.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String errorCode,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<ValidationErrorDetail> details,
        String correlationId
) {
    public ApiErrorResponse(Instant timestamp, int status, String error, String message, String path) {
        this(timestamp, status, error, message, path, error, null, null);
    }

    public ApiErrorResponse(Instant timestamp, int status, String error, String message, String path, String errorCode) {
        this(timestamp, status, error, message, path, errorCode, null, null);
    }

    public ApiErrorResponse(Instant timestamp, int status, String error, String message, String path, String errorCode, List<ValidationErrorDetail> details) {
        this(timestamp, status, error, message, path, errorCode, details, null);
    }
}
