package com.chaosreplay.api;

import com.chaosreplay.api.dto.FailureDetailResponse;
import com.chaosreplay.api.dto.TelemetryEventResponse;
import com.chaosreplay.api.dto.TraceCorrelationResponse;
import com.chaosreplay.api.dto.TraceReconstructionResponse;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.Severity;
import com.chaosreplay.exception.GlobalExceptionHandler;
import com.chaosreplay.exception.ResourceNotFoundException;
import com.chaosreplay.service.TraceCorrelationService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TraceCorrelationController.class)
@Import(GlobalExceptionHandler.class)
class TraceCorrelationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TraceCorrelationService correlationService;

    @Test
    @DisplayName("GET /api/v1/traces/{traceId} returns 200 OK with correlated trace data")
    void getTrace_traceExists_returns200() throws Exception {
        String traceId = "trace-mvc-1";
        Instant t1 = Instant.parse("2026-09-21T11:00:00Z");

        TelemetryEventResponse event = new TelemetryEventResponse(
                1L, "evt-1", t1, "api-gateway", "gw-01", EventType.REQUEST, Severity.INFO,
                traceId, "req-1", "GET /items", "Inbound request", Map.of(), t1
        );

        TraceCorrelationResponse mockResponse = new TraceCorrelationResponse(
                traceId, 1, false, Severity.INFO, List.of("api-gateway"), List.of(event)
        );

        when(correlationService.getTraceCorrelation(traceId)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/traces/{traceId}", traceId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.traceId").value(traceId))
                .andExpect(jsonPath("$.eventCount").value(1))
                .andExpect(jsonPath("$.hasErrors").value(false))
                .andExpect(jsonPath("$.highestSeverity").value("INFO"))
                .andExpect(jsonPath("$.services[0]").value("api-gateway"))
                .andExpect(jsonPath("$.events[0].eventId").value("evt-1"));
    }

    @Test
    @DisplayName("GET /api/v1/traces/{traceId} returns 404 NOT FOUND when trace does not exist")
    void getTrace_unknownTrace_returns404() throws Exception {
        String traceId = "trace-unknown";
        when(correlationService.getTraceCorrelation(traceId))
                .thenThrow(new ResourceNotFoundException("Trace not found: " + traceId));

        mockMvc.perform(get("/api/v1/traces/{traceId}", traceId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message", containsString("Trace not found: trace-unknown")));
    }

    @Test
    @DisplayName("GET /api/v1/traces/{traceId}/reconstruction returns 200 OK with reconstruction timeline")
    void reconstructTrace_traceExists_returns200() throws Exception {
        String traceId = "trace-recon-mvc";
        Instant t1 = Instant.parse("2026-09-21T12:00:00Z");
        Instant t2 = Instant.parse("2026-09-21T12:00:01.500Z");

        TelemetryEventResponse event1 = new TelemetryEventResponse(
                1L, "evt-1", t1, "cart-service", "c-1", EventType.REQUEST, Severity.INFO,
                traceId, "req-1", "POST /cart/checkout", "Checkout initiated", Map.of(), t1
        );
        TelemetryEventResponse event2 = new TelemetryEventResponse(
                2L, "evt-2", t2, "payment-service", "p-1", EventType.ERROR, Severity.ERROR,
                traceId, "req-1", "POST /charge", "Card provider unreachable", Map.of(), t2
        );

        FailureDetailResponse firstFailure = new FailureDetailResponse(
                "evt-2", "payment-service", "p-1", t2, EventType.ERROR, Severity.ERROR,
                "POST /charge", "Card provider unreachable"
        );

        TraceReconstructionResponse mockResponse = new TraceReconstructionResponse(
                traceId, 2, List.of("cart-service", "payment-service"),
                t1, t2, 1500L, true, 1, firstFailure, List.of(event1, event2)
        );

        when(correlationService.reconstructTrace(traceId)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/traces/{traceId}/reconstruction", traceId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.traceId").value(traceId))
                .andExpect(jsonPath("$.eventCount").value(2))
                .andExpect(jsonPath("$.durationMs").value(1500))
                .andExpect(jsonPath("$.hasFailure").value(true))
                .andExpect(jsonPath("$.failureCount").value(1))
                .andExpect(jsonPath("$.firstFailure.eventId").value("evt-2"))
                .andExpect(jsonPath("$.firstFailure.serviceName").value("payment-service"))
                .andExpect(jsonPath("$.firstFailure.message").value("Card provider unreachable"))
                .andExpect(jsonPath("$.timeline[0].eventId").value("evt-1"))
                .andExpect(jsonPath("$.timeline[1].eventId").value("evt-2"));
    }

    @Test
    @DisplayName("GET /api/v1/traces/{traceId}/reconstruction returns 404 NOT FOUND when trace does not exist")
    void reconstructTrace_unknownTrace_returns404() throws Exception {
        String traceId = "missing-trace";
        when(correlationService.reconstructTrace(traceId))
                .thenThrow(new ResourceNotFoundException("Trace not found: " + traceId));

        mockMvc.perform(get("/api/v1/traces/{traceId}/reconstruction", traceId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message", containsString("Trace not found: missing-trace")));
    }
}

