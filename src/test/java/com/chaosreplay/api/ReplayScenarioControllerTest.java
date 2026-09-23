package com.chaosreplay.api;

import com.chaosreplay.api.dto.ReplayExecutionResponse;
import com.chaosreplay.api.dto.ReplayScenarioEventResponse;
import com.chaosreplay.api.dto.ReplayScenarioResponse;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.ReplayScenarioStatus;
import com.chaosreplay.domain.Severity;
import com.chaosreplay.exception.GlobalExceptionHandler;
import com.chaosreplay.exception.ResourceNotFoundException;
import com.chaosreplay.service.ReplayExecutionService;
import com.chaosreplay.service.ReplayScenarioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReplayScenarioController.class)
@Import(GlobalExceptionHandler.class)
class ReplayScenarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReplayScenarioService scenarioService;

    @MockitoBean
    private ReplayExecutionService executionService;

    @Test
    @DisplayName("POST /api/v1/replay/scenarios/traces/{traceId} returns 201 Created when newly generated")
    void createScenario_newlyCreated_returns201() throws Exception {
        String traceId = "trace-new-1";
        ReplayScenarioEventResponse ev = new ReplayScenarioEventResponse(
                1, "evt-1", 0L, "gateway", "gw-1", EventType.REQUEST, Severity.INFO,
                traceId, "req-1", "POST /order", "Start", Map.of()
        );
        ReplayScenarioResponse response = new ReplayScenarioResponse(
                "scen-new-123", traceId, Instant.now(), 1, 0, 0L, ReplayScenarioStatus.CREATED, List.of(ev)
        );

        when(scenarioService.createScenario(traceId))
                .thenReturn(new ReplayScenarioService.ReplayScenarioCreationResult(response, true));

        mockMvc.perform(post("/api/v1/replay/scenarios/traces/{traceId}", traceId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.scenarioId").value("scen-new-123"))
                .andExpect(jsonPath("$.sourceTraceId").value(traceId))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.events[0].eventId").value("evt-1"));
    }

    @Test
    @DisplayName("POST /api/v1/replay/scenarios/traces/{traceId} returns 200 OK when scenario already exists (idempotent)")
    void createScenario_alreadyExists_returns200() throws Exception {
        String traceId = "trace-existing-1";
        ReplayScenarioResponse response = new ReplayScenarioResponse(
                "scen-existing-456", traceId, Instant.now(), 2, 1, 500L, ReplayScenarioStatus.CREATED, List.of()
        );

        when(scenarioService.createScenario(traceId))
                .thenReturn(new ReplayScenarioService.ReplayScenarioCreationResult(response, false));

        mockMvc.perform(post("/api/v1/replay/scenarios/traces/{traceId}", traceId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenarioId").value("scen-existing-456"))
                .andExpect(jsonPath("$.sourceTraceId").value(traceId));
    }

    @Test
    @DisplayName("POST /api/v1/replay/scenarios/traces/{traceId} returns 404 Not Found when trace has no telemetry")
    void createScenario_unknownTrace_returns404() throws Exception {
        String traceId = "trace-missing";
        when(scenarioService.createScenario(traceId))
                .thenThrow(new ResourceNotFoundException("Trace not found: " + traceId));

        mockMvc.perform(post("/api/v1/replay/scenarios/traces/{traceId}", traceId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("Trace not found: trace-missing")));
    }

    @Test
    @DisplayName("GET /api/v1/replay/scenarios/{scenarioId} returns 200 OK with scenario details")
    void getScenario_exists_returns200() throws Exception {
        String scenarioId = "scen-get-1";
        ReplayScenarioResponse response = new ReplayScenarioResponse(
                scenarioId, "trace-1", Instant.now(), 1, 0, 100L, ReplayScenarioStatus.COMPLETED, List.of()
        );

        when(scenarioService.getScenario(scenarioId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/replay/scenarios/{scenarioId}", scenarioId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenarioId").value(scenarioId))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("GET /api/v1/replay/scenarios/{scenarioId} returns 404 Not Found when scenario does not exist")
    void getScenario_missing_returns404() throws Exception {
        String scenarioId = "scen-not-found";
        when(scenarioService.getScenario(scenarioId))
                .thenThrow(new ResourceNotFoundException("Scenario not found: " + scenarioId));

        mockMvc.perform(get("/api/v1/replay/scenarios/{scenarioId}", scenarioId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("Scenario not found: scen-not-found")));
    }

    @Test
    @DisplayName("POST /api/v1/replay/scenarios/{scenarioId}/execute returns 200 OK with simulation results")
    void executeScenario_valid_returns200() throws Exception {
        String scenarioId = "scen-exec-1";
        ReplayExecutionResponse response = new ReplayExecutionResponse(
                scenarioId, ReplayScenarioStatus.COMPLETED, Instant.now(), Instant.now(),
                5L, 3, 1, "Deterministic simulation replay completed successfully"
        );

        when(executionService.executeScenario(scenarioId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/replay/scenarios/{scenarioId}/execute", scenarioId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenarioId").value(scenarioId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.eventsProcessed").value(3))
                .andExpect(jsonPath("$.failuresSimulated").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/replay/scenarios/{scenarioId}/execute returns 409 Conflict when already RUNNING")
    void executeScenario_alreadyRunning_returns409() throws Exception {
        String scenarioId = "scen-busy";
        when(executionService.executeScenario(scenarioId))
                .thenThrow(new IllegalStateException("Scenario '" + scenarioId + "' is currently RUNNING"));

        mockMvc.perform(post("/api/v1/replay/scenarios/{scenarioId}/execute", scenarioId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message", containsString("currently RUNNING")));
    }

    @Test
    @DisplayName("GET /api/v1/replay/scenarios/traces/{traceId} returns 200 OK with list of scenarios")
    void getScenariosForTrace_returns200() throws Exception {
        String traceId = "trace-list-1";
        ReplayScenarioResponse sc = new ReplayScenarioResponse(
                "scen-1", traceId, Instant.now(), 1, 0, 0L, ReplayScenarioStatus.CREATED, List.of()
        );

        when(scenarioService.getScenariosForTrace(traceId)).thenReturn(List.of(sc));

        mockMvc.perform(get("/api/v1/replay/scenarios/traces/{traceId}", traceId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].scenarioId").value("scen-1"))
                .andExpect(jsonPath("$[0].sourceTraceId").value(traceId));
    }
}

