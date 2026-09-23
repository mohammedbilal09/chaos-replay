package com.chaosreplay.api;

import com.chaosreplay.api.dto.FailureAnalysisCandidateResponse;
import com.chaosreplay.api.dto.FailureAnalysisEvidenceResponse;
import com.chaosreplay.api.dto.FailureAnalysisResponse;
import com.chaosreplay.domain.FailureAnalysisConclusion;
import com.chaosreplay.domain.FailureAnalysisStatus;
import com.chaosreplay.domain.FailureCandidateType;
import com.chaosreplay.domain.FailureEvidenceType;
import com.chaosreplay.exception.GlobalExceptionHandler;
import com.chaosreplay.exception.ResourceNotFoundException;
import com.chaosreplay.service.FailureAnalysisService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FailureAnalysisController.class)
@Import(GlobalExceptionHandler.class)
class FailureAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FailureAnalysisService analysisService;

    private FailureAnalysisResponse createSampleResponse(String analysisId, String traceId) {
        FailureAnalysisEvidenceResponse ev = new FailureAnalysisEvidenceResponse(
                1, "evt-1", FailureEvidenceType.FIRST_FAILURE, "Earliest failure"
        );
        FailureAnalysisCandidateResponse cand = new FailureAnalysisCandidateResponse(
                1, "payment-service", "evt-1", "DATABASE", "ERROR",
                FailureCandidateType.DATABASE_FAILURE, new BigDecimal("0.9300"),
                Instant.now(), "Database failure in payment-service", List.of(ev)
        );
        return new FailureAnalysisResponse(
                analysisId,
                traceId,
                "scen-1",
                "verify-1",
                FailureAnalysisStatus.COMPLETED,
                FailureAnalysisConclusion.ROOT_CAUSE_CANDIDATE,
                new BigDecimal("0.9300"),
                3,
                1,
                1,
                "payment-service",
                "evt-1",
                "Primary failure candidate: DATABASE_FAILURE in payment-service",
                List.of(cand),
                Instant.now()
        );
    }

    @Test
    @DisplayName("1. POST /api/v1/analysis/traces/{traceId} returns 201 Created on fresh analysis")
    void analyzeTrace_fresh_returns201() throws Exception {
        String traceId = "trace-new-1";
        FailureAnalysisResponse response = createSampleResponse("analysis-new-12345", traceId);

        when(analysisService.analyzeTrace(traceId))
                .thenReturn(new FailureAnalysisService.AnalysisResult(response, true));

        mockMvc.perform(post("/api/v1/analysis/traces/{traceId}", traceId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.analysisId").value("analysis-new-12345"))
                .andExpect(jsonPath("$.traceId").value(traceId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.conclusion").value("ROOT_CAUSE_CANDIDATE"))
                .andExpect(jsonPath("$.candidates[0].candidateType").value("DATABASE_FAILURE"))
                .andExpect(jsonPath("$.candidates[0].evidence[0].evidenceType").value("FIRST_FAILURE"));
    }

    @Test
    @DisplayName("2. Repeat POST /api/v1/analysis/traces/{traceId} returns 200 OK on idempotent call")
    void analyzeTrace_repeat_returns200() throws Exception {
        String traceId = "trace-repeat-1";
        FailureAnalysisResponse response = createSampleResponse("analysis-repeat-12345", traceId);

        when(analysisService.analyzeTrace(traceId))
                .thenReturn(new FailureAnalysisService.AnalysisResult(response, false));

        mockMvc.perform(post("/api/v1/analysis/traces/{traceId}", traceId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.analysisId").value("analysis-repeat-12345"))
                .andExpect(jsonPath("$.traceId").value(traceId));
    }

    @Test
    @DisplayName("3. GET /api/v1/analysis/{analysisId} returns 200 OK with analysis details")
    void getAnalysis_exists_returns200() throws Exception {
        String analysisId = "analysis-get-1";
        FailureAnalysisResponse response = createSampleResponse(analysisId, "trace-get-1");

        when(analysisService.getAnalysis(analysisId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/analysis/{analysisId}", analysisId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value(analysisId))
                .andExpect(jsonPath("$.primaryServiceName").value("payment-service"));
    }

    @Test
    @DisplayName("4. GET /api/v1/analysis/{analysisId} returns 404 Not Found when analysis is missing")
    void getAnalysis_missing_returns404() throws Exception {
        String analysisId = "analysis-missing";
        when(analysisService.getAnalysis(analysisId))
                .thenThrow(new ResourceNotFoundException("Analysis not found: " + analysisId));

        mockMvc.perform(get("/api/v1/analysis/{analysisId}", analysisId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("Analysis not found: analysis-missing")));
    }

    @Test
    @DisplayName("5. GET /api/v1/analysis/traces/{traceId} returns 200 OK with latest analysis")
    void getLatestAnalysisForTrace_exists_returns200() throws Exception {
        String traceId = "trace-latest-1";
        FailureAnalysisResponse response = createSampleResponse("analysis-latest-1", traceId);

        when(analysisService.getLatestAnalysisForTrace(traceId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/analysis/traces/{traceId}", traceId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value("analysis-latest-1"))
                .andExpect(jsonPath("$.traceId").value(traceId));
    }

    @Test
    @DisplayName("6. GET /api/v1/analysis/traces/{traceId} returns 404 Not Found when trace has no analysis")
    void getLatestAnalysisForTrace_missing_returns404() throws Exception {
        String traceId = "trace-missing";
        when(analysisService.getLatestAnalysisForTrace(traceId))
                .thenThrow(new ResourceNotFoundException("No failure analysis found for trace: " + traceId));

        mockMvc.perform(get("/api/v1/analysis/traces/{traceId}", traceId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("No failure analysis found for trace: trace-missing")));
    }

    @Test
    @DisplayName("7. GET /api/v1/analysis/traces/{traceId}/history returns 200 OK with analysis history")
    void getAnalysisHistoryForTrace_exists_returns200() throws Exception {
        String traceId = "trace-hist-1";
        FailureAnalysisResponse resp1 = createSampleResponse("analysis-h1", traceId);
        FailureAnalysisResponse resp2 = createSampleResponse("analysis-h2", traceId);

        when(analysisService.getAnalysisHistoryForTrace(traceId)).thenReturn(List.of(resp1, resp2));

        mockMvc.perform(get("/api/v1/analysis/traces/{traceId}/history", traceId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].analysisId").value("analysis-h1"))
                .andExpect(jsonPath("$[1].analysisId").value("analysis-h2"));
    }

    @Test
    @DisplayName("8. POST /api/v1/analysis/traces/{traceId} returns 404 Not Found when trace has no telemetry")
    void analyzeTrace_unknownTrace_returns404() throws Exception {
        String traceId = "trace-nonexistent";
        when(analysisService.analyzeTrace(traceId))
                .thenThrow(new ResourceNotFoundException("Trace not found: " + traceId));

        mockMvc.perform(post("/api/v1/analysis/traces/{traceId}", traceId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("Trace not found: trace-nonexistent")));
    }
}

