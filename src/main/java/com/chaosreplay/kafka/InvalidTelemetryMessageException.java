package com.chaosreplay.kafka;

/**
 * Exception indicating an incoming Kafka telemetry message is malformed, invalid,
 * or unprocessable.
 * <p>
 * Non-retryable: messages triggering this exception are routed directly to the
 * dead-letter topic without wasting consumer retries.
 */
public class InvalidTelemetryMessageException extends RuntimeException {

    public InvalidTelemetryMessageException(String message) {
        super(message);
    }

    public InvalidTelemetryMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}

