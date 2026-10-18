package com.chaosreplay.security;

import com.chaosreplay.api.TelemetryController;
import com.chaosreplay.api.TraceCorrelationController;
import com.chaosreplay.api.dto.CreateTelemetryEventRequest;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.Severity;
import com.chaosreplay.exception.GlobalExceptionHandler;
import com.chaosreplay.service.TelemetryService;
import com.chaosreplay.service.TraceCorrelationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {TelemetryController.class, TraceCorrelationController.class})
@Import({GlobalExceptionHandler.class, SecurityHeadersFilter.class})
class ApiSecurityHardeningTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TelemetryService telemetryService;

    @MockitoBean
    private TraceCorrelationService traceCorrelationService;

    @Test
    @DisplayName("Path variable with invalid characters is rejected with 400 and VALIDATION_FAILED")
    void invalidPathVariableCharacters_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/traces/trace@#$invalid!/reconstruction")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isNotEmpty())
                .andExpect(header().exists("X-Correlation-ID"));
    }

    @Test
    @DisplayName("Path variable exceeding 64 characters is rejected with 400")
    void oversizedPathVariable_returns400() throws Exception {
        String longTraceId = "a".repeat(65);

        mockMvc.perform(get("/api/v1/traces/" + longTraceId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Telemetry event with metadata exceeding 50 entries is rejected with 400")
    void metadataExceeding50Entries_returns400() throws Exception {
        Map<String, Object> excessiveMetadata = new HashMap<>();
        for (int i = 0; i < 51; i++) {
            excessiveMetadata.put("key_" + i, "val_" + i);
        }

        CreateTelemetryEventRequest request = new CreateTelemetryEventRequest(
                "evt-sec-test-1",
                Instant.now(),
                "test-service",
                null,
                EventType.REQUEST,
                Severity.INFO,
                "trace-sec-1",
                null,
                "GET /test",
                "Testing metadata limits",
                excessiveMetadata
        );

        mockMvc.perform(post("/api/v1/telemetry/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message", containsString("metadata must not contain more than 50 entries")));
    }

    @Test
    @DisplayName("Telemetry event with blank metadata key is rejected with 400")
    void metadataBlankKey_returns400() throws Exception {
        Map<String, Object> invalidMetadata = Map.of("   ", "val");

        CreateTelemetryEventRequest request = new CreateTelemetryEventRequest(
                "evt-sec-test-2",
                Instant.now(),
                "test-service",
                null,
                EventType.REQUEST,
                Severity.INFO,
                "trace-sec-1",
                null,
                "GET /test",
                "Testing blank key",
                invalidMetadata
        );

        mockMvc.perform(post("/api/v1/telemetry/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message", containsString("metadata keys must be non-blank")));
    }

    @Test
    @DisplayName("Telemetry event with invalid characters in eventId is rejected with 400")
    void invalidEventIdCharacters_returns400() throws Exception {
        CreateTelemetryEventRequest request = new CreateTelemetryEventRequest(
                "evt<script>alert(1)</script>",
                Instant.now(),
                "test-service",
                null,
                EventType.REQUEST,
                Severity.INFO,
                "trace-sec-1",
                null,
                "GET /test",
                "Testing XSS attempt",
                Map.of()
        );

        mockMvc.perform(post("/api/v1/telemetry/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message", containsString("eventId contains invalid characters")));
    }

    @Test
    @DisplayName("Malformed JSON payload does not leak internal Java package names or class hierarchies")
    void malformedJson_doesNotLeakInternalPackageNames() throws Exception {
        String malformedJson = """
                {
                    "eventId": "evt-123",
                    "eventType": "INVALID_ENUM_NAME",
                    "severity": "INFO"
                }
                """;

        mockMvc.perform(post("/api/v1/telemetry/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"))
                .andExpect(content().string(not(containsString("com.chaosreplay.domain"))))
                .andExpect(content().string(not(containsString("com.chaosreplay.api"))));
    }

    @Test
    @DisplayName("Unsupported media type returns 415 with structured ApiErrorResponse")
    void unsupportedMediaType_returns415() throws Exception {
        mockMvc.perform(post("/api/v1/telemetry/events")
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<event><id>123</id></event>")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}

