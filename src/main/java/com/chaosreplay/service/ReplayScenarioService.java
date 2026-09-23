package com.chaosreplay.service;

import com.chaosreplay.api.dto.ReplayScenarioEventResponse;
import com.chaosreplay.api.dto.ReplayScenarioResponse;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.ReplayScenario;
import com.chaosreplay.domain.ReplayScenarioEvent;
import com.chaosreplay.domain.ReplayScenarioStatus;
import com.chaosreplay.domain.Severity;
import com.chaosreplay.domain.TelemetryEvent;
import com.chaosreplay.exception.ResourceNotFoundException;
import com.chaosreplay.repository.ReplayScenarioEventRepository;
import com.chaosreplay.repository.ReplayScenarioRepository;
import com.chaosreplay.repository.TelemetryEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service managing deterministic replay scenario generation and retrieval.
 * Derived scenarios preserve event lineage, sequence, and relative offset timing from telemetry.
 */
@Service
public class ReplayScenarioService {

    private static final Logger log = LoggerFactory.getLogger(ReplayScenarioService.class);

    private final TelemetryEventRepository telemetryEventRepository;
    private final ReplayScenarioRepository replayScenarioRepository;
    private final ReplayScenarioEventRepository replayScenarioEventRepository;

    public ReplayScenarioService(
            TelemetryEventRepository telemetryEventRepository,
            ReplayScenarioRepository replayScenarioRepository,
            ReplayScenarioEventRepository replayScenarioEventRepository
    ) {
        this.telemetryEventRepository = telemetryEventRepository;
        this.replayScenarioRepository = replayScenarioRepository;
        this.replayScenarioEventRepository = replayScenarioEventRepository;
    }

    /**
     * Encapsulates the result of scenario creation with an idempotency indicator.
     */
    public record ReplayScenarioCreationResult(ReplayScenarioResponse scenario, boolean newlyCreated) {
    }

    /**
     * Generates a deterministic replay scenario from the reconstructed telemetry of a source trace.
     * Idempotent: returns the existing scenario if one was already generated for this trace.
     *
     * @param traceId distributed trace identifier
     * @return creation result containing the scenario response and newlyCreated flag
     * @throws ResourceNotFoundException if no telemetry events exist for the trace
     */
    @Transactional
    public ReplayScenarioCreationResult createScenario(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new ResourceNotFoundException("Trace not found: " + traceId);
        }

        String cleanTraceId = traceId.trim();

        // Idempotency fast-path: return existing scenario if already created for this trace
        Optional<ReplayScenario> existing = replayScenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtAsc(cleanTraceId);
        if (existing.isPresent()) {
            log.info("Idempotent scenario retrieval: scenario [{}] already exists for trace [{}]",
                    existing.get().getScenarioId(), cleanTraceId);
            return new ReplayScenarioCreationResult(buildScenarioResponse(existing.get()), false);
        }

        // Retrieve immutable source telemetry ordered deterministically by timestamp ASC, eventId ASC
        List<TelemetryEvent> telemetryEvents = telemetryEventRepository.findAllByTraceIdOrderByTimestampAscEventIdAsc(cleanTraceId);
        if (telemetryEvents.isEmpty()) {
            log.info("Cannot generate scenario: no telemetry events found for trace [{}]", cleanTraceId);
            throw new ResourceNotFoundException("Trace not found: " + cleanTraceId);
        }

        Instant startTime = telemetryEvents.get(0).getTimestamp();
        Instant endTime = telemetryEvents.get(telemetryEvents.size() - 1).getTimestamp();
        long durationMs = Duration.between(startTime, endTime).toMillis();

        int failureCount = (int) telemetryEvents.stream()
                .filter(this::isFailure)
                .count();

        String scenarioId = generateDeterministicScenarioId(cleanTraceId, telemetryEvents);

        // Check again by scenarioId in case of concurrent execution
        Optional<ReplayScenario> existingByScenarioId = replayScenarioRepository.findByScenarioId(scenarioId);
        if (existingByScenarioId.isPresent()) {
            return new ReplayScenarioCreationResult(buildScenarioResponse(existingByScenarioId.get()), false);
        }

        ReplayScenario scenario = new ReplayScenario(
                scenarioId,
                cleanTraceId,
                telemetryEvents.size(),
                failureCount,
                durationMs,
                ReplayScenarioStatus.CREATED
        );

        List<ReplayScenarioEvent> scenarioEvents = new ArrayList<>();
        for (int i = 0; i < telemetryEvents.size(); i++) {
            TelemetryEvent te = telemetryEvents.get(i);
            long offsetMs = Duration.between(startTime, te.getTimestamp()).toMillis();
            int sequenceNumber = i + 1; // 1-indexed deterministic sequence

            scenarioEvents.add(new ReplayScenarioEvent(
                    scenarioId,
                    te.getEventId(),
                    offsetMs,
                    sequenceNumber,
                    te.getServiceName(),
                    te.getServiceInstance(),
                    te.getEventType(),
                    te.getSeverity(),
                    te.getTraceId(),
                    te.getRequestId(),
                    te.getOperation(),
                    te.getMessage(),
                    te.getMetadata()
            ));
        }

        try {
            ReplayScenario savedScenario = replayScenarioRepository.saveAndFlush(scenario);
            List<ReplayScenarioEvent> savedEvents = replayScenarioEventRepository.saveAllAndFlush(scenarioEvents);

            log.info("Generated replay scenario [{}] for trace [{}] with {} events, {} failures, duration {}ms",
                    scenarioId, cleanTraceId, savedEvents.size(), failureCount, durationMs);

            List<ReplayScenarioEventResponse> eventResponses = savedEvents.stream()
                    .map(ReplayScenarioEventResponse::fromEntity)
                    .toList();

            return new ReplayScenarioCreationResult(
                    ReplayScenarioResponse.fromEntity(savedScenario, eventResponses),
                    true
            );
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrency collision for scenarioId [{}] on trace [{}], falling back to existing record",
                    scenarioId, cleanTraceId);
            ReplayScenario fallback = replayScenarioRepository.findByScenarioId(scenarioId)
                    .orElseThrow(() -> ex);
            return new ReplayScenarioCreationResult(buildScenarioResponse(fallback), false);
        }
    }

    /**
     * Retrieves an existing replay scenario by its deterministic scenario identifier.
     *
     * @param scenarioId deterministic scenario identifier
     * @return replay scenario response containing ordered event snapshots
     * @throws ResourceNotFoundException if the scenario is not found
     */
    @Transactional(readOnly = true)
    public ReplayScenarioResponse getScenario(String scenarioId) {
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new ResourceNotFoundException("Scenario not found: " + scenarioId);
        }

        ReplayScenario scenario = replayScenarioRepository.findByScenarioId(scenarioId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Scenario not found: " + scenarioId.trim()));

        return buildScenarioResponse(scenario);
    }

    /**
     * Retrieves all replay scenarios generated from a specific source trace identifier.
     *
     * @param traceId source trace identifier
     * @return list of replay scenario responses
     */
    @Transactional(readOnly = true)
    public List<ReplayScenarioResponse> getScenariosForTrace(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new ResourceNotFoundException("Trace not found: " + traceId);
        }

        List<ReplayScenario> scenarios = replayScenarioRepository.findBySourceTraceIdOrderByCreatedAtAsc(traceId.trim());
        return scenarios.stream()
                .map(this::buildScenarioResponse)
                .toList();
    }

    private ReplayScenarioResponse buildScenarioResponse(ReplayScenario scenario) {
        List<ReplayScenarioEvent> events = replayScenarioEventRepository
                .findAllByScenarioIdOrderBySequenceNumberAsc(scenario.getScenarioId());

        List<ReplayScenarioEventResponse> eventResponses = events.stream()
                .map(ReplayScenarioEventResponse::fromEntity)
                .toList();

        return ReplayScenarioResponse.fromEntity(scenario, eventResponses);
    }

    /**
     * Derives a deterministic scenario identifier from the source trace ID and ordered event sequence.
     * Format: scen-{128-bit SHA-256 hex prefix (32 chars)}
     */
    private String generateDeterministicScenarioId(String traceId, List<TelemetryEvent> events) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder input = new StringBuilder(traceId).append(":");
            for (TelemetryEvent event : events) {
                input.append(event.getEventId())
                        .append("@")
                        .append(event.getTimestamp().toEpochMilli())
                        .append(";");
            }
            byte[] hash = digest.digest(input.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return "scen-" + hexString.substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 message digest algorithm not available", e);
        }
    }

    /**
     * Preserves empirical failure identification rule established in Step 3.
     */
    private boolean isFailure(TelemetryEvent event) {
        return event.getSeverity() == Severity.ERROR
                || event.getSeverity() == Severity.FATAL
                || event.getEventType() == EventType.ERROR;
    }
}

