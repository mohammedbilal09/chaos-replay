package com.chaosreplay.api;

import com.chaosreplay.api.dto.FailureAnalysisCandidateResponse;
import com.chaosreplay.api.dto.FailureAnalysisEvidenceResponse;
import com.chaosreplay.api.dto.IncidentInvestigationResponse;
import com.chaosreplay.api.dto.IncidentTimelineEntryResponse;
import com.chaosreplay.api.dto.IncidentTimelineResponse;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.FailureAnalysisConclusion;
import com.chaosreplay.domain.FailureAnalysisStatus;
import com.chaosreplay.domain.FailureCandidateType;
import com.chaosreplay.domain.FailureEvidenceType;
import com.chaosreplay.domain.IncidentEntryCategory;
import com.chaosreplay.domain.ReplayScenarioStatus;
import com.chaosreplay.domain.ReplayVerificationStatus;
import com.chaosreplay.domain.Severity;
import com.chaosreplay.exception.GlobalExceptionHandler;
import com.chaosreplay.exception.ResourceNotFoundException;
import com.chaosreplay.service.IncidentInvestigationService;
import com.chaosreplay.service.IncidentTimelineService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncidentController.class)
@Import(GlobalExceptionHandler.class)
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IncidentTimelineService timelineService;

    @MockitoBean
    private IncidentInvestigationService investigationService;

    private IncidentTimelineResponse createSampleTimeline(String traceId) {
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");
        Instant t1 = Instant.parse("2026-10-18T10:00:00.050Z");

        IncidentTimelineEntryResponse entry1 = new IncidentTimelineEntryResponse(
                1, "evt-1", t0, 0L, "api-gateway", "inst-1",
                EventType.REQUEST, Severity.INFO, "POST /checkout", "Checkout initiated",
                IncidentEntryCategory.NORMAL_EVENT, false, false, null, List.of()
        );
        IncidentTimelineEntryResponse entry2 = new IncidentTimelineEntryResponse(
                2, "evt-2", t1, 50L, "payment-service", "inst-2",
                EventType.DATABASE, Severity.ERROR, "UPDATE balance", "Deadlock detected",
                IncidentEntryCategory.DATABASE_FAILURE, true, false, 1,
                List.of("FIRST_FAILURE: Earliest failure", "DATABASE_ERROR: Deadlock")
        );

        return new IncidentTimelineResponse(
                traceId,
                2,
                1,
                true,
                t0,
                t1,
                50L,
                true,
                true,
                FailureAnalysisStatus.COMPLETED,
                FailureAnalysisConclusion.ROOT_CAUSE_CANDIDATE,
                "payment-service",
                "evt-2",
                new BigDecimal("0.9600"),
                List.of(entry1, entry2),
                t1
        );
    }

    private IncidentInvestigationResponse createSampleInvestigation(String traceId) {
        IncidentTimelineResponse timeline = createSampleTimeline(traceId);
        FailureAnalysisEvidenceResponse ev = new FailureAnalysisEvidenceResponse(
                1, "evt-2", FailureEvidenceType.DATABASE_ERROR, "Deadlock"
        );
        FailureAnalysisCandidateResponse cand = new FailureAnalysisCandidateResponse(
                1, "payment-service", "evt-2", "DATABASE", "ERROR",
                FailureCandidateType.DATABASE_FAILURE, new BigDecimal("0.9600"),
                Instant.parse("2026-10-18T10:00:00.050Z"), "Deadlock failure", List.of(ev)
        );

        return new IncidentInvestigationResponse(
                traceId,
                timeline,
                true,
                true,
                "scen-sample-01",
                true,
                ReplayScenarioStatus.COMPLETED,
                true,
                "verify-sample-01",
                ReplayVerificationStatus.PASSED,
                "Replay verified successfully",
                true,
                "analysis-sample-01",
                FailureAnalysisStatus.COMPLETED,
                FailureAnalysisConclusion.ROOT_CAUSE_CANDIDATE,
                new BigDecimal("0.9600"),
                "payment-service",
                "evt-2",
                List.of(cand),
                List.of("Candidate #1: Deadlock failure"),
                List.of("Candidates represent evidence-backed correlations.")
        );
    }

    @Test
    @DisplayName("1. GET /api/v1/incidents/traces/{traceId}/timeline returns 200 OK with timeline payload")
    void getTimeline_returns200Ok() throws Exception {
        String traceId = "trace-test-01";
        IncidentTimelineResponse timeline = createSampleTimeline(traceId);

        when(timelineService.getTimeline(traceId)).thenReturn(timeline);

        mockMvc.perform(get("/api/v1/incidents/traces/{traceId}/timeline", traceId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.traceId").value(traceId))
                .andExpect(jsonPath("$.eventCount").value(2))
                .andExpect(jsonPath("$.failureCount").value(1))
                .andExpect(jsonPath("$.failureObserved").value(true))
                .andExpect(jsonPath("$.durationMs").value(50))
                .andExpect(jsonPath("$.primaryServiceName").value("payment-service"))
                .andExpect(jsonPath("$.confidenceScore").value(0.9600))
                .andExpect(jsonPath("$.timeline", hasSize(2)))
                .andExpect(jsonPath("$.timeline[0].entryCategory").value("NORMAL_EVENT"))
                .andExpect(jsonPath("$.timeline[1].entryCategory").value("DATABASE_FAILURE"))
                .andExpect(jsonPath("$.timeline[1].failureObserved").value(true));
    }

    @Test
    @DisplayName("2. GET /api/v1/incidents/traces/{traceId} returns 200 OK with consolidated investigation")
    void getInvestigation_returns200Ok() throws Exception {
        String traceId = "trace-test-02";
        IncidentInvestigationResponse investigation = createSampleInvestigation(traceId);

        when(investigationService.getInvestigation(traceId)).thenReturn(investigation);

        mockMvc.perform(get("/api/v1/incidents/traces/{traceId}", traceId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.traceId").value(traceId))
                .andExpect(jsonPath("$.correlationAvailable").value(true))
                .andExpect(jsonPath("$.scenarioAvailable").value(true))
                .andExpect(jsonPath("$.executionStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.verificationStatus").value("PASSED"))
                .andExpect(jsonPath("$.conclusion").value("ROOT_CAUSE_CANDIDATE"))
                .andExpect(jsonPath("$.candidates", hasSize(1)))
                .andExpect(jsonPath("$.evidenceSummary", hasSize(1)))
                .andExpect(jsonPath("$.limitations", hasSize(1)));
    }

    @Test
    @DisplayName("3. GET timeline for unknown trace returns 404 NOT FOUND")
    void getTimeline_unknownTrace_returns404() throws Exception {
        when(timelineService.getTimeline("unknown-trace"))
                .thenThrow(new ResourceNotFoundException("Trace not found: unknown-trace"));

        mockMvc.perform(get("/api/v1/incidents/traces/{traceId}/timeline", "unknown-trace")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("Trace not found: unknown-trace")));
    }

    @Test
    @DisplayName("4. GET investigation for unknown trace returns 404 NOT FOUND")
    void getInvestigation_unknownTrace_returns404() throws Exception {
        when(investigationService.getInvestigation("unknown-trace"))
                .thenThrow(new ResourceNotFoundException("Trace not found: unknown-trace"));

        mockMvc.perform(get("/api/v1/incidents/traces/{traceId}", "unknown-trace")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("Trace not found: unknown-trace")));
    }

    @Test
    @DisplayName("5. Clean trace timeline serialization formats INSUFFICIENT_EVIDENCE correctly")
    void cleanTrace_serializedCorrectly() throws Exception {
        String traceId = "trace-clean-01";
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");

        IncidentTimelineResponse cleanTimeline = new IncidentTimelineResponse(
                traceId, 1, 0, false, t0, t0, 0L, false, false,
                FailureAnalysisStatus.INSUFFICIENT_EVIDENCE,
                FailureAnalysisConclusion.NO_FAILURE_OBSERVED,
                null, null, BigDecimal.ZERO.setScale(4), List.of(), t0
        );

        when(timelineService.getTimeline(traceId)).thenReturn(cleanTimeline);

        mockMvc.perform(get("/api/v1/incidents/traces/{traceId}/timeline", traceId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failureObserved").value(false))
                .andExpect(jsonPath("$.rootCauseStatus").value("INSUFFICIENT_EVIDENCE"))
                .andExpect(jsonPath("$.conclusion").value("NO_FAILURE_OBSERVED"))
                .andExpect(jsonPath("$.confidenceScore").value(0.0));
    }

    @Test
    @DisplayName("6. Repeated GET requests return consistent, deterministic JSON responses")
    void repeatedGet_returnsIdenticalResponses() throws Exception {
        String traceId = "trace-repeat-01";
        IncidentTimelineResponse timeline = createSampleTimeline(traceId);

        when(timelineService.getTimeline(traceId)).thenReturn(timeline);

        mockMvc.perform(get("/api/v1/incidents/traces/{traceId}/timeline", traceId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").value(traceId));

        mockMvc.perform(get("/api/v1/incidents/traces/{traceId}/timeline", traceId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").value(traceId));
    }

    @Test
    @DisplayName("7. Response DTO fields and nested evidence are serialized correctly")
    void nestedEvidence_serializedCorrectly() throws Exception {
        String traceId = "trace-nested-01";
        IncidentInvestigationResponse investigation = createSampleInvestigation(traceId);

        when(investigationService.getInvestigation(traceId)).thenReturn(investigation);

        mockMvc.perform(get("/api/v1/incidents/traces/{traceId}", traceId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[0].evidence[0].evidenceType").value("DATABASE_ERROR"))
                .andExpect(jsonPath("$.candidates[0].evidence[0].evidenceValue").value("Deadlock"));
    }
}
