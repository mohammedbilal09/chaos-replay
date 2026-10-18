package com.chaosreplay.integration;

import com.chaosreplay.api.dto.CreateTelemetryEventRequest;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.Severity;
import com.chaosreplay.service.TelemetryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "chaosreplay.kafka.enabled=false"
})
class ObservabilityIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TelemetryService telemetryService;

    @Test
    @DisplayName("Actuator health check returns UP with component statuses but hides sensitive connection details")
    void healthEndpoint_reportsUpWithoutSensitiveDetails() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.livenessState.status").value("UP"))
                .andExpect(jsonPath("$.components.readinessState.status").value("UP"))
                // Ensure sensitive internal details (connection strings, paths) are never leaked
                .andExpect(content().string(not(containsString("jdbc:postgresql"))))
                .andExpect(content().string(not(containsString("/Users/"))))
                .andExpect(content().string(not(containsString("validationQuery"))));
    }

    @Test
    @DisplayName("Liveness and Readiness probes report UP status")
    void probes_reportUp() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Actuator metrics endpoint exposes application and telemetry meters")
    void metricsEndpoint_listsMetrics() throws Exception {
        mockMvc.perform(get("/actuator/metrics")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("http.server.requests")))
                .andExpect(content().string(containsString("jvm.memory.used")));
    }

    @Test
    @DisplayName("Telemetry ingestion records custom metrics and exposes them via Actuator")
    void ingestionAndTimeline_populateMetrics() throws Exception {
        String traceId = "trace-obs-" + UUID.randomUUID();
        String eventId1 = UUID.randomUUID().toString();
        String eventId2 = UUID.randomUUID().toString();

        // 1. Ingest valid telemetry event via REST
        String payload1 = """
                {
                    "eventId": "%s",
                    "timestamp": "%s",
                    "serviceName": "order-service",
                    "eventType": "REQUEST",
                    "severity": "INFO",
                    "traceId": "%s"
                }
                """.formatted(eventId1, Instant.now(), traceId);

        mockMvc.perform(post("/api/v1/telemetry/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload1))
                .andExpect(status().isCreated());

        // 2. Ingest duplicate event to trigger duplicate metric
        mockMvc.perform(post("/api/v1/telemetry/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload1))
                .andExpect(status().isConflict());

        // 3. Send invalid payload (missing eventId) to trigger invalid metric
        String invalidPayload = """
                {
                    "timestamp": "%s",
                    "serviceName": "order-service",
                    "eventType": "REQUEST",
                    "severity": "INFO"
                }
                """.formatted(Instant.now());

        mockMvc.perform(post("/api/v1/telemetry/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest());

        // 4. Ingest second event and query timeline to trigger timeline metric
        String payload2 = """
                {
                    "eventId": "%s",
                    "timestamp": "%s",
                    "serviceName": "order-service",
                    "eventType": "ERROR",
                    "severity": "ERROR",
                    "traceId": "%s"
                }
                """.formatted(eventId2, Instant.now().plusMillis(50), traceId);

        mockMvc.perform(post("/api/v1/telemetry/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload2))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/incidents/traces/" + traceId + "/timeline")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // 5. Verify metrics via Actuator
        mockMvc.perform(get("/actuator/metrics/chaosreplay.telemetry.ingested.total?tag=source:rest&tag=status:success")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value").value(2.0));

        mockMvc.perform(get("/actuator/metrics/chaosreplay.telemetry.duplicate.total?tag=source:rest")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value").value(1.0));

        mockMvc.perform(get("/actuator/metrics/chaosreplay.telemetry.invalid.total?tag=reason:validation")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value").value(1.0));

        mockMvc.perform(get("/actuator/metrics/chaosreplay.incident.timeline.duration?tag=status:success")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].statistic").value("COUNT"))
                .andExpect(jsonPath("$.measurements[0].value").value(1.0));
    }
}
