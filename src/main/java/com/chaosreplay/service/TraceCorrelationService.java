package com.chaosreplay.service;

import com.chaosreplay.api.dto.FailureDetailResponse;
import com.chaosreplay.api.dto.RequestCorrelationResponse;
import com.chaosreplay.api.dto.TelemetryEventResponse;
import com.chaosreplay.api.dto.TraceCorrelationResponse;
import com.chaosreplay.api.dto.TraceReconstructionResponse;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.Severity;
import com.chaosreplay.domain.TelemetryEvent;
import com.chaosreplay.exception.ResourceNotFoundException;
import com.chaosreplay.repository.TelemetryEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Service providing correlation and deterministic failure reconstruction across ingested telemetry.
 * Computes execution metrics, ordered timelines, and first-failure diagnostics.
 */
@Service
public class TraceCorrelationService {

    private static final Logger log = LoggerFactory.getLogger(TraceCorrelationService.class);

    private final TelemetryEventRepository repository;

    public TraceCorrelationService(TelemetryEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Retrieves all telemetry events correlated by the given trace identifier,
     * ordered deterministically by occurrence timestamp ascending with eventId ascending as tie-breaker.
     *
     * @param traceId distributed trace identifier
     * @return correlated trace response with summary metrics and ordered events
     * @throws ResourceNotFoundException if no events exist for the trace
     */
    @Transactional(readOnly = true)
    public TraceCorrelationResponse getTraceCorrelation(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new ResourceNotFoundException("Trace not found: " + traceId);
        }

        List<TelemetryEvent> events = repository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId.trim());
        if (events.isEmpty()) {
            log.info("No telemetry events found for traceId [{}]", traceId);
            throw new ResourceNotFoundException("Trace not found: " + traceId);
        }

        int eventCount = events.size();
        boolean hasErrors = events.stream().anyMatch(this::isFailure);
        Severity highestSeverity = calculateHighestSeverity(events);
        List<String> services = extractServices(events);
        List<TelemetryEventResponse> eventResponses = events.stream()
                .map(TelemetryEventResponse::fromEntity)
                .toList();

        log.debug("Correlated trace [{}] with {} events across services {}", traceId, eventCount, services);
        return new TraceCorrelationResponse(traceId, eventCount, hasErrors, highestSeverity, services, eventResponses);
    }

    /**
     * Retrieves all telemetry events correlated by the given request identifier,
     * ordered deterministically by occurrence timestamp ascending with eventId ascending as tie-breaker.
     *
     * @param requestId ingress request identifier
     * @return correlated request response with summary metrics and ordered events
     * @throws ResourceNotFoundException if no events exist for the request
     */
    @Transactional(readOnly = true)
    public RequestCorrelationResponse getRequestCorrelation(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new ResourceNotFoundException("Request not found: " + requestId);
        }

        List<TelemetryEvent> events = repository.findAllByRequestIdOrderByTimestampAscEventIdAsc(requestId.trim());
        if (events.isEmpty()) {
            log.info("No telemetry events found for requestId [{}]", requestId);
            throw new ResourceNotFoundException("Request not found: " + requestId);
        }

        int eventCount = events.size();
        boolean hasErrors = events.stream().anyMatch(this::isFailure);
        Severity highestSeverity = calculateHighestSeverity(events);
        List<String> services = extractServices(events);
        List<TelemetryEventResponse> eventResponses = events.stream()
                .map(TelemetryEventResponse::fromEntity)
                .toList();

        log.debug("Correlated request [{}] with {} events across services {}", requestId, eventCount, services);
        return new RequestCorrelationResponse(requestId, eventCount, hasErrors, highestSeverity, services, eventResponses);
    }

    /**
     * Reconstructs the end-to-end execution timeline and failure analysis for a given trace identifier.
     * Deterministically calculates duration, involved services, failure count, and first-failure occurrence.
     *
     * @param traceId distributed trace identifier
     * @return reconstructed trace timeline with failure diagnostics
     * @throws ResourceNotFoundException if no events exist for the trace
     */
    @Transactional(readOnly = true)
    public TraceReconstructionResponse reconstructTrace(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new ResourceNotFoundException("Trace not found: " + traceId);
        }

        List<TelemetryEvent> events = repository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId.trim());
        if (events.isEmpty()) {
            log.info("Cannot reconstruct timeline: no telemetry events found for traceId [{}]", traceId);
            throw new ResourceNotFoundException("Trace not found: " + traceId);
        }

        int eventCount = events.size();
        List<String> services = extractServices(events);
        Instant startTime = events.get(0).getTimestamp();
        Instant endTime = events.get(events.size() - 1).getTimestamp();
        long durationMs = Duration.between(startTime, endTime).toMillis();

        List<TelemetryEvent> failureEvents = events.stream()
                .filter(this::isFailure)
                .toList();

        int failureCount = failureEvents.size();
        boolean hasFailure = failureCount > 0;
        FailureDetailResponse firstFailure = hasFailure
                ? FailureDetailResponse.fromEntity(failureEvents.get(0))
                : null;

        List<TelemetryEventResponse> timeline = events.stream()
                .map(TelemetryEventResponse::fromEntity)
                .toList();

        log.info("Reconstructed trace [{}] timeline: duration={}ms, events={}, failures={}",
                traceId, durationMs, eventCount, failureCount);

        return new TraceReconstructionResponse(
                traceId,
                eventCount,
                services,
                startTime,
                endTime,
                durationMs,
                hasFailure,
                failureCount,
                firstFailure,
                timeline
        );
    }

    /**
     * Empirical failure predicate: an event represents a failure if its severity is ERROR or FATAL,
     * or if its event type is ERROR. A single event matching multiple criteria is evaluated as a single failure.
     */
    private boolean isFailure(TelemetryEvent event) {
        return event.getSeverity() == Severity.ERROR
                || event.getSeverity() == Severity.FATAL
                || event.getEventType() == EventType.ERROR;
    }

    private Severity calculateHighestSeverity(List<TelemetryEvent> events) {
        return events.stream()
                .map(TelemetryEvent::getSeverity)
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(Severity::ordinal))
                .orElse(Severity.INFO);
    }

    private List<String> extractServices(List<TelemetryEvent> events) {
        return events.stream()
                .map(TelemetryEvent::getServiceName)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}

