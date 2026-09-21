package com.chaosreplay.api.dto;

public record HealthResponse(
    String status,
    String service
) {
}

