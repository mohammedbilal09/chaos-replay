package com.chaosreplay.api;

import com.chaosreplay.api.dto.CreateTelemetryEventRequest;
import com.chaosreplay.api.dto.PagedResponse;
import com.chaosreplay.api.dto.TelemetryEventResponse;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.Severity;
import com.chaosreplay.service.TelemetryQueryFilter;
import com.chaosreplay.service.TelemetryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * REST controller exposing endpoints for ingesting and querying distributed telemetry events.
 */
@RestController
@RequestMapping("/api/v1/telemetry/events")
public class TelemetryController {

    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    /**
     * Ingests a single telemetry event.
     *
     * @param request validated telemetry event payload
     * @return 201 CREATED with the persisted event payload
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TelemetryEventResponse> ingestEvent(
            @Valid @RequestBody CreateTelemetryEventRequest request
    ) {
        TelemetryEventResponse response = telemetryService.ingestEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Queries telemetry events with optional filters and bounded pagination.
     * Results are ordered by timestamp descending (newest events first).
     *
     * @param serviceName optional filter by originating service
     * @param eventType   optional filter by event type enum
     * @param severity    optional filter by severity enum
     * @param traceId     optional filter by distributed trace identifier
     * @param requestId   optional filter by ingress request identifier
     * @param from        optional filter for events occurring at or after this ISO-8601 UTC timestamp
     * @param to          optional filter for events occurring at or before this ISO-8601 UTC timestamp
     * @param page        0-indexed page number (default: 0)
     * @param size        number of records per page (default: 20, max: 100)
     * @return 200 OK with paginated telemetry events
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PagedResponse<TelemetryEventResponse>> queryEvents(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) EventType eventType,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException("Page index must not be negative");
        }
        if (size <= 0 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        }

        TelemetryQueryFilter filter = new TelemetryQueryFilter(
                serviceName,
                eventType,
                severity,
                traceId,
                requestId,
                from,
                to
        );

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        PagedResponse<TelemetryEventResponse> response = telemetryService.queryEvents(filter, pageable);
        return ResponseEntity.ok(response);
    }
}

