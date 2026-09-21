package com.chaosreplay.exception;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public record ApiErrorResponse(
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant timestamp,
    int status,
    String error,
    String message,
    String path
) {
}
