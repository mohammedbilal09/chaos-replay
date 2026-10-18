package com.chaosreplay.api;

import com.chaosreplay.api.dto.IncidentInvestigationResponse;
import com.chaosreplay.api.dto.IncidentTimelineResponse;
import com.chaosreplay.service.IncidentInvestigationService;
import com.chaosreplay.service.IncidentTimelineService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing read-only incident investigation and reconstructed failure timeline endpoints.
 */
@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentTimelineService timelineService;
    private final IncidentInvestigationService investigationService;

    public IncidentController(
            IncidentTimelineService timelineService,
            IncidentInvestigationService investigationService
    ) {
        this.timelineService = timelineService;
        this.investigationService = investigationService;
    }

    /**
     * Retrieves an enriched, chronological incident timeline for a distributed trace.
     *
     * @param traceId distributed trace identifier
     * @return 200 OK with incident timeline response, or 404 NOT FOUND if trace does not exist
     */
    @GetMapping(value = "/traces/{traceId}/timeline", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IncidentTimelineResponse> getTimeline(@PathVariable String traceId) {
        IncidentTimelineResponse response = timelineService.getTimeline(traceId);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a consolidated incident investigation view aggregating timeline, replay verification,
     * ranked root-cause candidates, and explicit evidence/limitations.
     *
     * @param traceId distributed trace identifier
     * @return 200 OK with consolidated investigation response, or 404 NOT FOUND if trace does not exist
     */
    @GetMapping(value = "/traces/{traceId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IncidentInvestigationResponse> getInvestigation(@PathVariable String traceId) {
        IncidentInvestigationResponse response = investigationService.getInvestigation(traceId);
        return ResponseEntity.ok(response);
    }
}

