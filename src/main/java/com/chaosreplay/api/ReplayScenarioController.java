package com.chaosreplay.api;

import com.chaosreplay.api.dto.ReplayExecutionResponse;
import com.chaosreplay.api.dto.ReplayScenarioResponse;
import com.chaosreplay.service.ReplayExecutionService;
import com.chaosreplay.service.ReplayScenarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing endpoints for generating, inspecting, and simulating deterministic failure replays.
 */
@RestController
@RequestMapping("/api/v1/replay/scenarios")
public class ReplayScenarioController {

    private final ReplayScenarioService scenarioService;
    private final ReplayExecutionService executionService;

    public ReplayScenarioController(
            ReplayScenarioService scenarioService,
            ReplayExecutionService executionService
    ) {
        this.scenarioService = scenarioService;
        this.executionService = executionService;
    }

    /**
     * Generates a deterministic replay scenario from a reconstructed trace.
     * Idempotent: returns 201 CREATED if the scenario was newly created, or 200 OK if it already existed.
     *
     * @param traceId distributed trace identifier
     * @return 201 CREATED or 200 OK with the generated scenario payload
     */
    @PostMapping(value = "/traces/{traceId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReplayScenarioResponse> createScenario(@PathVariable String traceId) {
        ReplayScenarioService.ReplayScenarioCreationResult result = scenarioService.createScenario(traceId);
        if (result.newlyCreated()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(result.scenario());
        } else {
            return ResponseEntity.ok(result.scenario());
        }
    }

    /**
     * Retrieves an existing replay scenario by its deterministic scenario identifier.
     *
     * @param scenarioId deterministic scenario identifier
     * @return 200 OK with the replay scenario, or 404 NOT FOUND
     */
    @GetMapping(value = "/{scenarioId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReplayScenarioResponse> getScenario(@PathVariable String scenarioId) {
        ReplayScenarioResponse response = scenarioService.getScenario(scenarioId);
        return ResponseEntity.ok(response);
    }

    /**
     * Executes a replay scenario in SAFE SIMULATION MODE ONLY.
     *
     * @param scenarioId deterministic scenario identifier
     * @return 200 OK with simulation execution metrics, 404 NOT FOUND, or 409 CONFLICT if already running
     */
    @PostMapping(value = "/{scenarioId}/execute", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReplayExecutionResponse> executeScenario(@PathVariable String scenarioId) {
        ReplayExecutionResponse response = executionService.executeScenario(scenarioId);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves all replay scenarios derived from a specific source trace identifier.
     *
     * @param traceId distributed trace identifier
     * @return 200 OK with the list of replay scenarios
     */
    @GetMapping(value = "/traces/{traceId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ReplayScenarioResponse>> getScenariosForTrace(@PathVariable String traceId) {
        List<ReplayScenarioResponse> responses = scenarioService.getScenariosForTrace(traceId);
        return ResponseEntity.ok(responses);
    }
}

