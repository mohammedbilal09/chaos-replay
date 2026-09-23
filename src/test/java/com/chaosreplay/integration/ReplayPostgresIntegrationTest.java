package com.chaosreplay.integration;

import com.chaosreplay.api.dto.CreateTelemetryEventRequest;
import com.chaosreplay.api.dto.ReplayExecutionResponse;
import com.chaosreplay.api.dto.ReplayScenarioResponse;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.ReplayScenario;
import com.chaosreplay.domain.ReplayScenarioEvent;
import com.chaosreplay.domain.ReplayScenarioStatus;
import com.chaosreplay.domain.Severity;
import com.chaosreplay.exception.ResourceNotFoundException;
import com.chaosreplay.repository.ReplayScenarioEventRepository;
import com.chaosreplay.repository.ReplayScenarioRepository;
import com.chaosreplay.repository.TelemetryEventRepository;
import com.chaosreplay.service.ReplayExecutionService;
import com.chaosreplay.service.ReplayScenarioService;
import com.chaosreplay.service.TelemetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real PostgreSQL integration test utilizing Testcontainers for Step 4:
 * Deterministic Failure Replay & Scenario Generation.
 * <p>
 * Verifies V2 Flyway migration, scenario persistence, foreign key relationships,
 * JSONB metadata handling, sequence ordering, idempotency, and simulation execution
 * against a live PostgreSQL 16 container.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class ReplayPostgresIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ReplayPostgresIntegrationTest.class);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TelemetryEventRepository telemetryEventRepository;

    @Autowired
    private ReplayScenarioRepository scenarioRepository;

    @Autowired
    private ReplayScenarioEventRepository scenarioEventRepository;

    @Autowired
    private TelemetryService telemetryService;

    @Autowired
    private ReplayScenarioService scenarioService;

    @Autowired
    private ReplayExecutionService executionService;

    @BeforeEach
    void setUp() {
        log.info("==== VERIFIED: Testcontainers PostgreSQL is active at [{}] ====", postgres.getJdbcUrl());
        scenarioEventRepository.deleteAll();
        scenarioRepository.deleteAll();
        telemetryEventRepository.deleteAll();
    }

    @Test
    @DisplayName("Flyway V2 migration succeeds and scenario persists with JSONB metadata and foreign key")
    void scenarioGenerationAndPersistence_realPostgreSQL() {
        String traceId = "trace-pg-replay-1";
        Instant t0 = Instant.parse("2026-09-23T15:00:00.000Z");
        Instant t1 = Instant.parse("2026-09-23T15:00:00.500Z");
        Instant t2 = Instant.parse("2026-09-23T15:00:01.800Z");

        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-r-1", t0, "gateway-service", "gw-1", EventType.REQUEST, Severity.INFO,
                traceId, "req-1", "POST /orders", "Incoming order", Map.of("client", "mobile-app")
        ));
        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-r-2", t1, "order-service", "ord-1", EventType.REQUEST, Severity.INFO,
                traceId, "req-1", "POST /orders", "Creating order", Map.of()
        ));
        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-r-3", t2, "payment-service", "pay-1", EventType.EXTERNAL_CALL, Severity.ERROR,
                traceId, "req-1", "POST /charges", "Card gateway timeout", Map.of("provider", "stripe", "timeoutMs", 5000)
        ));

        ReplayScenarioService.ReplayScenarioCreationResult result = scenarioService.createScenario(traceId);

        assertThat(result.newlyCreated()).isTrue();
        ReplayScenarioResponse response = result.scenario();
        assertThat(response.scenarioId()).startsWith("scen-");
        assertThat(response.sourceTraceId()).isEqualTo(traceId);
        assertThat(response.eventCount()).isEqualTo(3);
        assertThat(response.failureCount()).isEqualTo(1);
        assertThat(response.durationMs()).isEqualTo(1800L);
        assertThat(response.status()).isEqualTo(ReplayScenarioStatus.CREATED);

        // Verify direct database persistence via repositories
        Optional<ReplayScenario> savedScenario = scenarioRepository.findByScenarioId(response.scenarioId());
        assertThat(savedScenario).isPresent();
        assertThat(savedScenario.get().getSourceTraceId()).isEqualTo(traceId);

        List<ReplayScenarioEvent> savedEvents = scenarioEventRepository
                .findAllByScenarioIdOrderBySequenceNumberAsc(response.scenarioId());
        assertThat(savedEvents).hasSize(3);

        // Verify sequence ordering and offsets
        assertThat(savedEvents.get(0).getSequenceNumber()).isEqualTo(1);
        assertThat(savedEvents.get(0).getOffsetMs()).isZero();
        assertThat(savedEvents.get(0).getMetadata()).containsEntry("client", "mobile-app");

        assertThat(savedEvents.get(1).getSequenceNumber()).isEqualTo(2);
        assertThat(savedEvents.get(1).getOffsetMs()).isEqualTo(500L);

        assertThat(savedEvents.get(2).getSequenceNumber()).isEqualTo(3);
        assertThat(savedEvents.get(2).getOffsetMs()).isEqualTo(1800L);
        assertThat(savedEvents.get(2).getMetadata())
                .containsEntry("provider", "stripe")
                .containsEntry("timeoutMs", 5000);
    }

    @Test
    @DisplayName("Scenario generation is strictly idempotent: returns existing scenario without duplicates")
    void scenarioGeneration_idempotent() {
        String traceId = "trace-pg-idempotent";
        Instant t0 = Instant.parse("2026-09-23T16:00:00Z");

        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-idem-1", t0, "auth-service", "auth-1", EventType.REQUEST, Severity.INFO,
                traceId, "req-idem", "POST /login", "Login", Map.of()
        ));

        // First creation call
        ReplayScenarioService.ReplayScenarioCreationResult firstCall = scenarioService.createScenario(traceId);
        assertThat(firstCall.newlyCreated()).isTrue();
        String scenarioId = firstCall.scenario().scenarioId();

        // Second creation call
        ReplayScenarioService.ReplayScenarioCreationResult secondCall = scenarioService.createScenario(traceId);
        assertThat(secondCall.newlyCreated()).isFalse();
        assertThat(secondCall.scenario().scenarioId()).isEqualTo(scenarioId);

        // Verify database table has exactly 1 scenario
        List<ReplayScenario> allScenarios = scenarioRepository.findBySourceTraceIdOrderByCreatedAtAsc(traceId);
        assertThat(allScenarios).hasSize(1);
    }

    @Test
    @DisplayName("executeScenario transitions status to COMPLETED and records metrics in PostgreSQL")
    void executeScenario_simulationUpdatesStatusInPostgres() {
        String traceId = "trace-pg-sim";
        Instant t0 = Instant.parse("2026-09-23T17:00:00Z");
        Instant t1 = Instant.parse("2026-09-23T17:00:01Z");

        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-s-1", t0, "order-service", "ord-1", EventType.REQUEST, Severity.INFO,
                traceId, "req-s", "POST /orders", "Start", Map.of()
        ));
        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-s-2", t1, "order-service", "ord-1", EventType.ERROR, Severity.FATAL,
                traceId, "req-s", "POST /orders", "Crash", Map.of()
        ));

        ReplayScenarioService.ReplayScenarioCreationResult creation = scenarioService.createScenario(traceId);
        String scenarioId = creation.scenario().scenarioId();

        ReplayExecutionResponse execResponse = executionService.executeScenario(scenarioId);

        assertThat(execResponse.scenarioId()).isEqualTo(scenarioId);
        assertThat(execResponse.status()).isEqualTo(ReplayScenarioStatus.COMPLETED);
        assertThat(execResponse.eventsProcessed()).isEqualTo(2);
        assertThat(execResponse.failuresSimulated()).isEqualTo(1);

        // Verify updated entity in PostgreSQL
        ReplayScenario updated = scenarioRepository.findByScenarioId(scenarioId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ReplayScenarioStatus.COMPLETED);
    }

    @Test
    @DisplayName("Database unique constraints enforce uniqueness on scenario_id and (scenario_id, sequence_number)")
    void databaseUniqueConstraints_enforced() {
        ReplayScenario s1 = new ReplayScenario(
                "scen-duplicate-test", "trace-x", 1, 0, 0L, ReplayScenarioStatus.CREATED
        );
        scenarioRepository.saveAndFlush(s1);

        ReplayScenario s2 = new ReplayScenario(
                "scen-duplicate-test", "trace-y", 1, 0, 0L, ReplayScenarioStatus.CREATED
        );
        assertThatThrownBy(() -> scenarioRepository.saveAndFlush(s2))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Unique constraint on (scenario_id, sequence_number)
        ReplayScenarioEvent ev1 = new ReplayScenarioEvent(
                "scen-duplicate-test", "evt-1", 0L, 1, "svc", null,
                EventType.REQUEST, Severity.INFO, "trace-x", null, null, null, Map.of()
        );
        scenarioEventRepository.saveAndFlush(ev1);

        ReplayScenarioEvent ev2 = new ReplayScenarioEvent(
                "scen-duplicate-test", "evt-2", 100L, 1, "svc", null,
                EventType.RESPONSE, Severity.INFO, "trace-x", null, null, null, Map.of()
        );
        assertThatThrownBy(() -> scenarioEventRepository.saveAndFlush(ev2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("getScenario and getScenariosForTrace retrieve scenarios correctly from PostgreSQL")
    void retrievalEndpoints_workAgainstPostgres() {
        String traceId = "trace-pg-retrieve";
        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-ret-1", Instant.now(), "svc", null, EventType.LOG, Severity.INFO,
                traceId, null, null, "Log", Map.of()
        ));

        ReplayScenarioService.ReplayScenarioCreationResult created = scenarioService.createScenario(traceId);
        String scenarioId = created.scenario().scenarioId();

        ReplayScenarioResponse byScenarioId = scenarioService.getScenario(scenarioId);
        assertThat(byScenarioId.scenarioId()).isEqualTo(scenarioId);

        List<ReplayScenarioResponse> byTraceId = scenarioService.getScenariosForTrace(traceId);
        assertThat(byTraceId).hasSize(1);
        assertThat(byTraceId.get(0).scenarioId()).isEqualTo(scenarioId);
    }

    @Test
    @DisplayName("Unknown trace and unknown scenario throw ResourceNotFoundException")
    void unknownResources_throwResourceNotFoundException() {
        assertThatThrownBy(() -> scenarioService.createScenario("missing-trace"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Trace not found: missing-trace");

        assertThatThrownBy(() -> scenarioService.getScenario("missing-scenario"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Scenario not found: missing-scenario");

        assertThatThrownBy(() -> executionService.executeScenario("missing-scenario"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Scenario not found: missing-scenario");
    }
}

