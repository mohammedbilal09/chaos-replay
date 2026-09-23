package com.chaosreplay.api;

import com.chaosreplay.api.dto.FailureAnalysisResponse;
import com.chaosreplay.service.FailureAnalysisService;
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
 * REST controller exposing endpoints for generating and retrieving deterministic, evidence-backed failure analyses.
 */
@RestController
@RequestMapping("/api/v1/analysis")
public class FailureAnalysisController {

    private final FailureAnalysisService analysisService;

    public FailureAnalysisController(FailureAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * Executes a deterministic failure analysis for a given trace.
     * Idempotent: returns 201 CREATED if newly analyzed, or 200 OK if identical analysis already exists.
     *
     * @param traceId distributed trace identifier
     * @return 201 CREATED or 200 OK with the analysis response payload
     */
    @PostMapping(value = "/traces/{traceId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FailureAnalysisResponse> analyzeTrace(@PathVariable String traceId) {
        FailureAnalysisService.AnalysisResult result = analysisService.analyzeTrace(traceId);
        if (result.newlyCreated()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(result.response());
        } else {
            return ResponseEntity.ok(result.response());
        }
    }

    /**
     * Retrieves an existing failure analysis by its deterministic analysis identifier.
     *
     * @param analysisId deterministic analysis identifier
     * @return 200 OK with analysis details, candidates, and evidence, or 404 NOT FOUND
     */
    @GetMapping(value = "/{analysisId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FailureAnalysisResponse> getAnalysis(@PathVariable String analysisId) {
        FailureAnalysisResponse response = analysisService.getAnalysis(analysisId);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves the latest failure analysis associated with a trace.
     *
     * @param traceId distributed trace identifier
     * @return 200 OK with latest analysis, or 404 NOT FOUND
     */
    @GetMapping(value = "/traces/{traceId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FailureAnalysisResponse> getLatestAnalysisForTrace(@PathVariable String traceId) {
        FailureAnalysisResponse response = analysisService.getLatestAnalysisForTrace(traceId);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves the complete failure analysis history for a trace ordered by creation time descending.
     *
     * @param traceId distributed trace identifier
     * @return 200 OK with list of analysis records, or 404 NOT FOUND
     */
    @GetMapping(value = "/traces/{traceId}/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<FailureAnalysisResponse>> getAnalysisHistoryForTrace(@PathVariable String traceId) {
        List<FailureAnalysisResponse> responses = analysisService.getAnalysisHistoryForTrace(traceId);
        return ResponseEntity.ok(responses);
    }
}

