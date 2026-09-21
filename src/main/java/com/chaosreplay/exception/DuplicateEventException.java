package com.chaosreplay.exception;

/**
 * Thrown when an ingestion request attempts to store an event with an eventId
 * that already exists in the system.
 */
public class DuplicateEventException extends RuntimeException {

    private final String eventId;

    public DuplicateEventException(String eventId) {
        super(String.format("Telemetry event with eventId '%s' already exists", eventId));
        this.eventId = eventId;
    }

    public DuplicateEventException(String eventId, Throwable cause) {
        super(String.format("Telemetry event with eventId '%s' already exists", eventId), cause);
        this.eventId = eventId;
    }

    public String getEventId() {
        return eventId;
    }
}

