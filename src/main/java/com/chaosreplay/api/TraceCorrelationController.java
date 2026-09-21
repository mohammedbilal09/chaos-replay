package com.chaosreplay.api;

import com.chaosreplay.api.dto.TraceCorrelationResponse;
import com.chaosreplay.api.dto.TraceReconstructionResponse;
import com.chaosreplay.service.TraceCorrelationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller providing distributed trace correlation and failure timeline reconstruction endpoints.
 */
@RestController
@RequestMapping("/api/v1/traces")
public class TraceCorrelationController {

    private final TraceCorrelationService correlationService;

    public TraceCorrelationController(TraceCorrelationService correlationService) {
        this.correlationService = correlationService;
    }

    /**
     * Retrieves all telemetry events for a given trace identifier, ordered chronologically.
     *
     * @param traceId distributed trace identifier
     * @return 200 OK with correlated trace payload, or 404 NOT FOUND if trace does not exist
     */
    @GetMapping(value = "/{traceId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TraceCorrelationResponse> getTrace(@PathVariable String traceId) {
        TraceCorrelationResponse response = correlationService.getTraceCorrelation(traceId);
        return ResponseEntity.ok(response);
    }

    /**
     * Reconstructs execution duration, involved services, and deterministic failure diagnostics for a trace.
     *
     * @param traceId distributed trace identifier
     * @return 200 OK with reconstructed execution timeline, or 404 NOT FOUND if trace does not exist
     */
    @GetMapping(value = "/{traceId}/reconstruction", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TraceReconstructionResponse> reconstructTrace(@PathVariable String traceId) {
        TraceReconstructionResponse response = correlationService.reconstructTrace(traceId);
        return ResponseEntity.ok(response);
    }
}

