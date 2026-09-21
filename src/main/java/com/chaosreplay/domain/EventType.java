package com.chaosreplay.domain;

/**
 * Controlled categorization of telemetry events captured across distributed services.
 * Extensible for future ChaosReplay correlation phases.
 */
public enum EventType {
    /**
     * Ingress or cross-service inbound invocation.
     */
    REQUEST,

    /**
     * Ingress or cross-service outbound response.
     */
    RESPONSE,

    /**
     * Application, network, or framework-level error.
     */
    ERROR,

    /**
     * Structured application log or diagnostic statement.
     */
    LOG,

    /**
     * Database query or transactional interaction.
     */
    DATABASE,

    /**
     * Outbound HTTP, gRPC, or third-party downstream call.
     */
    EXTERNAL_CALL
}

