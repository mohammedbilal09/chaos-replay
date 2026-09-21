package com.chaosreplay.integration;

import com.chaosreplay.api.dto.CreateTelemetryEventRequest;
import com.chaosreplay.api.dto.RequestCorrelationResponse;
import com.chaosreplay.api.dto.TraceCorrelationResponse;
import com.chaosreplay.api.dto.TraceReconstructionResponse;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.Severity;
import com.chaosreplay.exception.ResourceNotFoundException;
import com.chaosreplay.repository.TelemetryEventRepository;
import com.chaosreplay.service.TelemetryService;
import com.chaosreplay.service.TraceCorrelationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real PostgreSQL integration test utilizing Testcontainers for Step 3:
 * Telemetry Correlation and Deterministic Failure Reconstruction.
 * <p>
 * Verifies SQL ORDER BY timestamp ASC, event_id ASC, tie-breaker behavior,
 * multi-service trace aggregation, duration calculation, and first-failure identification
 * against a live PostgreSQL 16 database.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class CorrelationPostgresIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(CorrelationPostgresIntegrationTest.class);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TelemetryEventRepository repository;

    @Autowired
    private TelemetryService telemetryService;

    @Autowired
    private TraceCorrelationService correlationService;

    @BeforeEach
    void setUp() {
        log.info("==== VERIFIED: Testcontainers PostgreSQL is active at [{}] ====", postgres.getJdbcUrl());
        repository.deleteAll();
    }

    @Test
    @DisplayName("Trace correlation retrieves events in deterministic timestamp ASC, eventId ASC order from PostgreSQL")
    void traceCorrelation_deterministicOrderingAndTieBreaker() {
        String targetTrace = "trace-pg-order-test";
        String otherTrace = "trace-other";

        Instant t1 = Instant.parse("2026-09-21T10:00:00.000Z");
        Instant t2 = Instant.parse("2026-09-21T10:00:01.000Z");
        Instant t3 = Instant.parse("2026-09-21T10:00:01.000Z"); // Identical timestamp to t2 (tie-breaker needed)
        Instant t4 = Instant.parse("2026-09-21T10:00:02.000Z");

        // Ingest events intentionally out of chronological order
        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-d", t4, "order-service", "ord-1", EventType.RESPONSE, Severity.INFO,
                targetTrace, "req-1", "POST /orders", "Order completed", Map.of()
        ));
        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-c", t3, "payment-service", "pay-1", EventType.EXTERNAL_CALL, Severity.WARN,
                targetTrace, "req-1", "POST /charge", "Gateway retried", Map.of()
        ));
        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-b", t2, "payment-service", "pay-1", EventType.REQUEST, Severity.INFO,
                targetTrace, "req-1", "POST /charge", "Payment received", Map.of()
        ));
        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-a", t1, "api-gateway", "gw-1", EventType.REQUEST, Severity.INFO,
                targetTrace, "req-1", "POST /orders", "Inbound request", Map.of()
        ));
        // Noise event for a different trace
        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-noise", t1, "auth-service", "auth-1", EventType.REQUEST, Severity.INFO,
                otherTrace, "req-noise", "GET /health", "Health check", Map.of()
        ));

        TraceCorrelationResponse correlation = correlationService.getTraceCorrelation(targetTrace);

        assertThat(correlation.traceId()).isEqualTo(targetTrace);
        assertThat(correlation.eventCount()).isEqualTo(4);
        assertThat(correlation.hasErrors()).isFalse();
        assertThat(correlation.highestSeverity()).isEqualTo(Severity.WARN);
        assertThat(correlation.services()).containsExactly("api-gateway", "payment-service", "order-service");

        // Verify deterministic sorting: evt-a -> evt-b (t2, id: evt-b) -> evt-c (t3=t2, id: evt-c) -> evt-d
        assertThat(correlation.events()).extracting("eventId")
                .containsExactly("evt-a", "evt-b", "evt-c", "evt-d");
    }

    @Test
    @DisplayName("Request correlation retrieves events for specific requestId in deterministic order")
    void requestCorrelation_deterministicOrdering() {
        String targetRequest = "req-pg-test-42";
        Instant t1 = Instant.parse("2026-09-21T11:00:00Z");
        Instant t2 = Instant.parse("2026-09-21T11:00:00.500Z");

        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-req-2", t2, "user-service", "usr-1", EventType.RESPONSE, Severity.INFO,
                "trace-1", targetRequest, "GET /users/42", "Returned profile", Map.of()
        ));
        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-req-1", t1, "user-service", "usr-1", EventType.REQUEST, Severity.INFO,
                "trace-1", targetRequest, "GET /users/42", "Fetching profile", Map.of()
        ));

        RequestCorrelationResponse correlation = correlationService.getRequestCorrelation(targetRequest);

        assertThat(correlation.requestId()).isEqualTo(targetRequest);
        assertThat(correlation.eventCount()).isEqualTo(2);
        assertThat(correlation.events()).extracting("eventId")
                .containsExactly("evt-req-1", "evt-req-2");
    }

    @Test
    @DisplayName("Trace reconstruction calculates duration, identifies first failure and timeline against real DB")
    void traceReconstruction_computesMetricsAndIdentifiesFirstFailure() {
        String traceId = "trace-pg-recon";
        Instant t1 = Instant.parse("2026-09-21T12:00:00.000Z");
        Instant t2 = Instant.parse("2026-09-21T12:00:01.200Z");
        Instant t3 = Instant.parse("2026-09-21T12:00:02.500Z");
        Instant t4 = Instant.parse("2026-09-21T12:00:03.000Z");

        // Ingest events out of order
        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-recon-4", t4, "order-service", "ord-1", EventType.ERROR, Severity.FATAL,
                traceId, "req-recon", "POST /orders", "Transaction aborted", Map.of()
        ));
        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-recon-2", t2, "order-service", "ord-1", EventType.REQUEST, Severity.INFO,
                traceId, "req-recon", "POST /orders", "Order validating", Map.of()
        ));
        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-recon-3", t3, "payment-service", "pay-1", EventType.ERROR, Severity.ERROR,
                traceId, "req-recon", "POST /charges", "Connection timeout to stripe", Map.of("timeoutMs", 5000)
        ));
        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-recon-1", t1, "ingress-gateway", "gw-1", EventType.REQUEST, Severity.INFO,
                traceId, "req-recon", "POST /checkout", "Ingress request", Map.of()
        ));

        TraceReconstructionResponse reconstruction = correlationService.reconstructTrace(traceId);

        assertThat(reconstruction.traceId()).isEqualTo(traceId);
        assertThat(reconstruction.eventCount()).isEqualTo(4);
        assertThat(reconstruction.services()).containsExactly("ingress-gateway", "order-service", "payment-service");
        assertThat(reconstruction.startTime()).isEqualTo(t1);
        assertThat(reconstruction.endTime()).isEqualTo(t4);
        assertThat(reconstruction.durationMs()).isEqualTo(3000L); // 12:00:00 to 12:00:03 = 3000 ms
        assertThat(reconstruction.hasFailure()).isTrue();
        assertThat(reconstruction.failureCount()).isEqualTo(2); // evt-recon-3 and evt-recon-4

        // First failure must be evt-recon-3 (occurred at t3, before evt-recon-4 at t4)
        assertThat(reconstruction.firstFailure()).isNotNull();
        assertThat(reconstruction.firstFailure().eventId()).isEqualTo("evt-recon-3");
        assertThat(reconstruction.firstFailure().serviceName()).isEqualTo("payment-service");
        assertThat(reconstruction.firstFailure().serviceInstance()).isEqualTo("pay-1");
        assertThat(reconstruction.firstFailure().timestamp()).isEqualTo(t3);
        assertThat(reconstruction.firstFailure().eventType()).isEqualTo(EventType.ERROR);
        assertThat(reconstruction.firstFailure().severity()).isEqualTo(Severity.ERROR);
        assertThat(reconstruction.firstFailure().operation()).isEqualTo("POST /charges");
        assertThat(reconstruction.firstFailure().message()).isEqualTo("Connection timeout to stripe");

        // Verify timeline order
        assertThat(reconstruction.timeline()).extracting("eventId")
                .containsExactly("evt-recon-1", "evt-recon-2", "evt-recon-3", "evt-recon-4");
    }

    @Test
    @DisplayName("Trace correlation and reconstruction throw ResourceNotFoundException for unknown traceId")
    void unknownTrace_throwsResourceNotFoundException() {
        assertThatThrownBy(() -> correlationService.getTraceCorrelation("non-existent-trace"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Trace not found: non-existent-trace");

        assertThatThrownBy(() -> correlationService.reconstructTrace("non-existent-trace"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Trace not found: non-existent-trace");
    }

    @Test
    @DisplayName("Request correlation throws ResourceNotFoundException for unknown requestId")
    void unknownRequest_throwsResourceNotFoundException() {
        assertThatThrownBy(() -> correlationService.getRequestCorrelation("non-existent-request"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Request not found: non-existent-request");
    }
}

