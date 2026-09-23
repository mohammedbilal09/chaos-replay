package com.chaosreplay.service;

import com.chaosreplay.api.dto.ReplayExecutionResponse;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.ReplayScenario;
import com.chaosreplay.domain.ReplayScenarioEvent;
import com.chaosreplay.domain.ReplayScenarioStatus;
import com.chaosreplay.domain.Severity;
import com.chaosreplay.exception.ResourceNotFoundException;
import com.chaosreplay.repository.ReplayScenarioEventRepository;
import com.chaosreplay.repository.ReplayScenarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Service executing failure replay scenarios in SAFE SIMULATION MODE ONLY.
 * Never executes real external HTTP calls, never calls third-party APIs,
 * never alters production state, and treats metadata strictly as passive data.
 */
@Service
public class ReplayExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ReplayExecutionService.class);

    private final ReplayScenarioRepository scenarioRepository;
    private final ReplayScenarioEventRepository scenarioEventRepository;

    public ReplayExecutionService(
            ReplayScenarioRepository scenarioRepository,
            ReplayScenarioEventRepository scenarioEventRepository
    ) {
        this.scenarioRepository = scenarioRepository;
        this.scenarioEventRepository = scenarioEventRepository;
    }

    /**
     * Executes an existing replay scenario in deterministic simulation mode.
     * Processes snapshot events sequentially, calculates simulated failure counts,
     * and advances scenario status from CREATED to RUNNING to COMPLETED.
     *
     * @param scenarioId deterministic scenario identifier
     * @return summary of simulation execution results
     * @throws ResourceNotFoundException if the scenario is not found
     * @throws IllegalStateException    if the scenario is already in a RUNNING state
     */
    @Transactional
    public ReplayExecutionResponse executeScenario(String scenarioId) {
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new ResourceNotFoundException("Scenario not found: " + scenarioId);
        }

        String cleanScenarioId = scenarioId.trim();

        ReplayScenario scenario = scenarioRepository.findByScenarioId(cleanScenarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Scenario not found: " + cleanScenarioId));

        if (scenario.getStatus() == ReplayScenarioStatus.RUNNING) {
            log.warn("Cannot execute scenario [{}]: scenario is already RUNNING", cleanScenarioId);
            throw new IllegalStateException("Scenario '" + cleanScenarioId + "' is currently RUNNING");
        }

        Instant startedAt = Instant.now();
        scenario.setStatus(ReplayScenarioStatus.RUNNING);
        scenarioRepository.saveAndFlush(scenario);

        List<ReplayScenarioEvent> events = scenarioEventRepository
                .findAllByScenarioIdOrderBySequenceNumberAsc(cleanScenarioId);

        log.info("Starting simulation replay for scenario [{}] containing {} events",
                cleanScenarioId, events.size());

        int eventsProcessed = 0;
        int failuresSimulated = 0;

        for (ReplayScenarioEvent event : events) {
            // SAFE SIMULATION: evaluate event snapshot without external network calls or thread sleeping
            eventsProcessed++;
            if (isFailure(event)) {
                failuresSimulated++;
                log.debug("Simulated failure at sequence #{}: [service={}, operation={}, severity={}]",
                        event.getSequenceNumber(), event.getServiceName(), event.getOperation(), event.getSeverity());
            }
        }

        Instant completedAt = Instant.now();
        long executionDurationMs = Duration.between(startedAt, completedAt).toMillis();

        scenario.setStatus(ReplayScenarioStatus.COMPLETED);
        scenarioRepository.saveAndFlush(scenario);

        log.info("Completed simulation replay for scenario [{}]: {} events processed, {} failures simulated in {}ms",
                cleanScenarioId, eventsProcessed, failuresSimulated, executionDurationMs);

        return new ReplayExecutionResponse(
                cleanScenarioId,
                scenario.getStatus(),
                startedAt,
                completedAt,
                executionDurationMs,
                eventsProcessed,
                failuresSimulated,
                "Deterministic simulation replay completed successfully"
        );
    }

    private boolean isFailure(ReplayScenarioEvent event) {
        return event.getSeverity() == Severity.ERROR
                || event.getSeverity() == Severity.FATAL
                || event.getEventType() == EventType.ERROR;
    }
}

