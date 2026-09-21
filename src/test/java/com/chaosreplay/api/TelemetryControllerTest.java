package com.chaosreplay.api;

import com.chaosreplay.api.dto.CreateTelemetryEventRequest;
import com.chaosreplay.api.dto.PagedResponse;
import com.chaosreplay.api.dto.TelemetryEventResponse;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.Severity;
import com.chaosreplay.exception.DuplicateEventException;
import com.chaosreplay.exception.GlobalExceptionHandler;
import com.chaosreplay.service.TelemetryQueryFilter;
import com.chaosreplay.service.TelemetryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TelemetryController.class)
@Import(GlobalExceptionHandler.class)
class TelemetryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TelemetryService telemetryService;

    @Test
    @DisplayName("POST /api/v1/telemetry/events with valid payload returns 201 Created and persisted response")
    void ingestEvent_validPayload_returns201() throws Exception {
        TelemetryEventResponse mockResponse = new TelemetryEventResponse(
                1L,
                "evt-001",
                Instant.parse("2026-09-21T15:30:00Z"),
                "payment-service",
                "payment-01",
                EventType.ERROR,
                Severity.ERROR,
                "trace-123",
                "req-456",
                "POST /payments",
                "Payment provider timeout",
                Map.of("provider", "example-provider", "timeoutMs", 5000),
                Instant.parse("2026-09-21T15:30:01Z")
        );

        when(telemetryService.ingestEvent(any(CreateTelemetryEventRequest.class))).thenReturn(mockResponse);

        String json = """
                {
                  "eventId": "evt-001",
                  "timestamp": "2026-09-21T15:30:00Z",
                  "serviceName": "payment-service",
                  "serviceInstance": "payment-01",
                  "eventType": "ERROR",
                  "severity": "ERROR",
                  "traceId": "trace-123",
                  "requestId": "req-456",
                  "operation": "POST /payments",
                  "message": "Payment provider timeout",
                  "metadata": {
                    "provider": "example-provider",
                    "timeoutMs": 5000
                  }
                }
                """;

        mockMvc.perform(post("/api/v1/telemetry/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.eventId").value("evt-001"))
                .andExpect(jsonPath("$.serviceName").value("payment-service"))
                .andExpect(jsonPath("$.eventType").value("ERROR"))
                .andExpect(jsonPath("$.severity").value("ERROR"))
                .andExpect(jsonPath("$.traceId").value("trace-123"))
                .andExpect(jsonPath("$.metadata.provider").value("example-provider"));
    }

    @Test
    @DisplayName("POST /api/v1/telemetry/events missing required fields returns 400 Bad Request")
    void ingestEvent_missingRequiredFields_returns400() throws Exception {
        String invalidJson = """
                {
                  "message": "Incomplete event without eventId, timestamp, serviceName, eventType, severity"
                }
                """;

        mockMvc.perform(post("/api/v1/telemetry/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message", containsString("eventId must not be blank")))
                .andExpect(jsonPath("$.message", containsString("serviceName must not be blank")))
                .andExpect(jsonPath("$.message", containsString("timestamp must not be null")))
                .andExpect(jsonPath("$.message", containsString("eventType must not be null")))
                .andExpect(jsonPath("$.message", containsString("severity must not be null")));
    }

    @Test
    @DisplayName("POST /api/v1/telemetry/events with invalid eventType returns 400 Bad Request")
    void ingestEvent_invalidEventType_returns400() throws Exception {
        String invalidJson = """
                {
                  "eventId": "evt-002",
                  "timestamp": "2026-09-21T15:30:00Z",
                  "serviceName": "auth-service",
                  "eventType": "NON_EXISTENT_TYPE",
                  "severity": "INFO"
                }
                """;

        mockMvc.perform(post("/api/v1/telemetry/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message", containsString("Malformed JSON or invalid field value")));
    }

    @Test
    @DisplayName("POST /api/v1/telemetry/events with invalid severity returns 400 Bad Request")
    void ingestEvent_invalidSeverity_returns400() throws Exception {
        String invalidJson = """
                {
                  "eventId": "evt-003",
                  "timestamp": "2026-09-21T15:30:00Z",
                  "serviceName": "auth-service",
                  "eventType": "REQUEST",
                  "severity": "CRITICAL_UNKNOWN"
                }
                """;

        mockMvc.perform(post("/api/v1/telemetry/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message", containsString("Malformed JSON or invalid field value")));
    }

    @Test
    @DisplayName("POST /api/v1/telemetry/events with duplicate eventId returns 409 Conflict")
    void ingestEvent_duplicateEventId_returns409() throws Exception {
        when(telemetryService.ingestEvent(any(CreateTelemetryEventRequest.class)))
                .thenThrow(new DuplicateEventException("evt-duplicate"));

        String json = """
                {
                  "eventId": "evt-duplicate",
                  "timestamp": "2026-09-21T15:30:00Z",
                  "serviceName": "auth-service",
                  "eventType": "LOG",
                  "severity": "INFO"
                }
                """;

        mockMvc.perform(post("/api/v1/telemetry/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message", containsString("Telemetry event with eventId 'evt-duplicate' already exists")));
    }

    @Test
    @DisplayName("GET /api/v1/telemetry/events returns 200 OK with PagedResponse")
    void queryEvents_success_returnsPagedResponse() throws Exception {
        TelemetryEventResponse event = new TelemetryEventResponse(
                1L,
                "evt-001",
                Instant.parse("2026-09-21T15:30:00Z"),
                "payment-service",
                "payment-01",
                EventType.ERROR,
                Severity.ERROR,
                "trace-123",
                "req-456",
                "POST /payments",
                "Timeout",
                Map.of(),
                Instant.parse("2026-09-21T15:30:01Z")
        );

        PagedResponse<TelemetryEventResponse> pagedResponse = new PagedResponse<>(
                List.of(event), 0, 20, 1L, 1, true, true
        );

        when(telemetryService.queryEvents(any(), any(Pageable.class))).thenReturn(pagedResponse);

        mockMvc.perform(get("/api/v1/telemetry/events")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content[0].eventId").value("evt-001"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/telemetry/events with search parameters propagates filter to service")
    void queryEvents_withFilters_propagatesParameters() throws Exception {
        PagedResponse<TelemetryEventResponse> emptyResponse = new PagedResponse<>(
                Collections.emptyList(), 0, 10, 0L, 0, true, true
        );
        when(telemetryService.queryEvents(any(), any(Pageable.class))).thenReturn(emptyResponse);

        mockMvc.perform(get("/api/v1/telemetry/events")
                        .param("serviceName", "payment-service")
                        .param("eventType", "ERROR")
                        .param("severity", "ERROR")
                        .param("traceId", "trace-999")
                        .param("requestId", "req-888")
                        .param("from", "2026-09-21T10:00:00Z")
                        .param("to", "2026-09-21T20:00:00Z")
                        .param("page", "1")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0));

        ArgumentCaptor<TelemetryQueryFilter> filterCaptor = ArgumentCaptor.forClass(TelemetryQueryFilter.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        verify(telemetryService).queryEvents(filterCaptor.capture(), pageableCaptor.capture());

        TelemetryQueryFilter capturedFilter = filterCaptor.getValue();
        assertThat(capturedFilter.serviceName()).isEqualTo("payment-service");
        assertThat(capturedFilter.eventType()).isEqualTo(EventType.ERROR);
        assertThat(capturedFilter.severity()).isEqualTo(Severity.ERROR);
        assertThat(capturedFilter.traceId()).isEqualTo("trace-999");
        assertThat(capturedFilter.requestId()).isEqualTo("req-888");
        assertThat(capturedFilter.from()).isEqualTo(Instant.parse("2026-09-21T10:00:00Z"));
        assertThat(capturedFilter.to()).isEqualTo(Instant.parse("2026-09-21T20:00:00Z"));

        Pageable capturedPageable = pageableCaptor.getValue();
        assertThat(capturedPageable.getPageNumber()).isEqualTo(1);
        assertThat(capturedPageable.getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("GET /api/v1/telemetry/events with invalid pagination parameters returns 400 Bad Request")
    void queryEvents_invalidPagination_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/telemetry/events")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Page index must not be negative")));

        mockMvc.perform(get("/api/v1/telemetry/events")
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Page size must be between 1 and 100")));

        mockMvc.perform(get("/api/v1/telemetry/events")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Page size must be between 1 and 100")));
    }
}

