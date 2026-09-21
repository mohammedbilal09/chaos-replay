package com.chaosreplay.api;

import com.chaosreplay.api.dto.RequestCorrelationResponse;
import com.chaosreplay.api.dto.TelemetryEventResponse;
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

@WebMvcTest(RequestCorrelationController.class)
@Import(GlobalExceptionHandler.class)
class RequestCorrelationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TraceCorrelationService correlationService;

    @Test
    @DisplayName("GET /api/v1/requests/{requestId} returns 200 OK with correlated request data")
    void getRequest_requestExists_returns200() throws Exception {
        String requestId = "req-mvc-1";
        Instant t1 = Instant.parse("2026-09-21T13:00:00Z");

        TelemetryEventResponse event = new TelemetryEventResponse(
                1L, "evt-req-1", t1, "auth-service", "auth-01", EventType.REQUEST, Severity.INFO,
                "trace-1", requestId, "POST /oauth/token", "Token issued", Map.of(), t1
        );

        RequestCorrelationResponse mockResponse = new RequestCorrelationResponse(
                requestId, 1, false, Severity.INFO, List.of("auth-service"), List.of(event)
        );

        when(correlationService.getRequestCorrelation(requestId)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/requests/{requestId}", requestId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.eventCount").value(1))
                .andExpect(jsonPath("$.hasErrors").value(false))
                .andExpect(jsonPath("$.highestSeverity").value("INFO"))
                .andExpect(jsonPath("$.services[0]").value("auth-service"))
                .andExpect(jsonPath("$.events[0].eventId").value("evt-req-1"));
    }

    @Test
    @DisplayName("GET /api/v1/requests/{requestId} returns 404 NOT FOUND when request does not exist")
    void getRequest_unknownRequest_returns404() throws Exception {
        String requestId = "req-missing";
        when(correlationService.getRequestCorrelation(requestId))
                .thenThrow(new ResourceNotFoundException("Request not found: " + requestId));

        mockMvc.perform(get("/api/v1/requests/{requestId}", requestId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message", containsString("Request not found: req-missing")));
    }
}

