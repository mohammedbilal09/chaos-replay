package com.chaosreplay.domain;

/**
 * Immutable record representing an observed event snapshot during replay simulation.
 * Keeps original TelemetryEvent immutable and clearly separates expected scenario events from observations.
 *
 * @param sequenceNumber 1-based order within the observed replay execution
 * @param eventId        identifier of the event observed (or simulated)
 * @param serviceName    service name reporting the observation
 * @param eventType      type of event observed
 * @param severity       severity of event observed
 * @param offsetMs       relative offset in milliseconds from replay start
 */
public record ReplayObservedEvent(
        int sequenceNumber,
        String eventId,
        String serviceName,
        EventType eventType,
        Severity severity,
        long offsetMs
) {
}

