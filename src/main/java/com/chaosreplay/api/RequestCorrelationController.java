package com.chaosreplay.api;

import com.chaosreplay.api.dto.RequestCorrelationResponse;
import com.chaosreplay.service.TraceCorrelationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller providing ingress request correlation endpoints.
 */
@RestController
@RequestMapping("/api/v1/requests")
public class RequestCorrelationController {

    private final TraceCorrelationService correlationService;

    public RequestCorrelationController(TraceCorrelationService correlationService) {
        this.correlationService = correlationService;
    }

    /**
     * Retrieves all telemetry events for a given ingress request identifier, ordered chronologically.
     *
     * @param requestId ingress request identifier
     * @return 200 OK with correlated request payload, or 404 NOT FOUND if request does not exist
     */
    @GetMapping(value = "/{requestId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RequestCorrelationResponse> getRequest(@PathVariable String requestId) {
        RequestCorrelationResponse response = correlationService.getRequestCorrelation(requestId);
        return ResponseEntity.ok(response);
    }
}

