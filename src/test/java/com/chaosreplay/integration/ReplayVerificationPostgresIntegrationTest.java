package com.chaosreplay.integration;

import com.chaosreplay.api.dto.CreateTelemetryEventRequest;
import com.chaosreplay.api.dto.ReplayScenarioResponse;
import com.chaosreplay.api.dto.ReplayVerificationResponse;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.ReplayObservedEvent;
import com.chaosreplay.domain.ReplayScenarioStatus;
import com.chaosreplay.domain.ReplayVerification;
import com.chaosreplay.domain.ReplayVerificationDifference;
import com.chaosreplay.domain.ReplayVerificationStatus;
import com.chaosreplay.domain.Severity;
import com.chaosreplay.domain.VerificationDifferenceType;
import com.chaosreplay.exception.ResourceNotFoundException;
import com.chaosreplay.repository.ReplayScenarioEventRepository;
import com.chaosreplay.repository.ReplayScenarioRepository;
import com.chaosreplay.repository.ReplayVerificationDifferenceRepository;
import com.chaosreplay.repository.ReplayVerificationRepository;
import com.chaosreplay.repository.TelemetryEventRepository;
import com.chaosreplay.service.ReplayExecutionService;
import com.chaosreplay.service.ReplayScenarioService;
import com.chaosreplay.service.ReplayVerificationService;
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
 * Real PostgreSQL integration test utilizing Testcontainers for Step 5:
 * Replay Verification & Failure Comparison.
 * <p>
 * Verifies V3 Flyway migration, verification persistence, difference persistence,
 * foreign key cascade behavior, unique constraints, idempotency, and end-to-end verification.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class ReplayVerificationPostgresIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ReplayVerificationPostgresIntegrationTest.class);

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
    private ReplayVerificationRepository verificationRepository;

    @Autowired
    private ReplayVerificationDifferenceRepository differenceRepository;

    @Autowired
    private TelemetryService telemetryService;

    @Autowired
    private ReplayScenarioService scenarioService;

    @Autowired
    private ReplayExecutionService executionService;

    @Autowired
    private ReplayVerificationService verificationService;

    @BeforeEach
    void setUp() {
        log.info("==== VERIFIED: Testcontainers PostgreSQL 16 is active at [{}] ====", postgres.getJdbcUrl());
        differenceRepository.deleteAll();
        verificationRepository.deleteAll();
        scenarioEventRepository.deleteAll();
        scenarioRepository.deleteAll();
        telemetryEventRepository.deleteAll();
    }

    @Test
    @DisplayName("1. Flyway V3 migration succeeds: persists ReplayVerification and ReplayVerificationDifference")
    void flywayV3_persistsVerificationAndDifferences() {
        String traceId = "trace-pg-v3-1";
        Instant t0 = Instant.parse("2026-09-23T18:00:00Z");

        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-v3-1", t0, "auth-service", "auth-1", EventType.REQUEST, Severity.INFO,
                traceId, "req-v3", "POST /token", "Token request", Map.of()
        ));

        ReplayScenarioService.ReplayScenarioCreationResult scenarioResult = scenarioService.createScenario(traceId);
        String scenarioId = scenarioResult.scenario().scenarioId();

        ReplayVerification verification = new ReplayVerification(
                "verify-direct-save-1",
                scenarioId,
                ReplayVerificationStatus.FAILED,
                1, 1, 0, 1, 100L, 100L,
                0, 0, 0, 1, 0, 0,
                "Direct test verification"
        );
        verificationRepository.saveAndFlush(verification);

        ReplayVerificationDifference diff = new ReplayVerificationDifference(
                "verify-direct-save-1",
                1,
                VerificationDifferenceType.SEVERITY_MISMATCH,
                "evt-v3-1", "evt-v3-1",
                "auth-service", "auth-service",
                "REQUEST", "REQUEST",
                "INFO", "ERROR",
                "Severity mismatched"
        );
        differenceRepository.saveAndFlush(diff);

        Optional<ReplayVerification> saved = verificationRepository.findByVerificationId("verify-direct-save-1");
        assertThat(saved).isPresent();
        assertThat(saved.get().getStatus()).isEqualTo(ReplayVerificationStatus.FAILED);

        List<ReplayVerificationDifference> diffs = differenceRepository
                .findAllByVerificationIdOrderBySequenceNumberAsc("verify-direct-save-1");
        assertThat(diffs).hasSize(1);
        assertThat(diffs.get(0).getDifferenceType()).isEqualTo(VerificationDifferenceType.SEVERITY_MISMATCH);
    }

    @Test
    @DisplayName("2. Foreign key cascade: deleting a scenario deletes its verifications and differences")
    void cascadeDelete_scenarioDeletesVerificationsAndDifferences() {
        String traceId = "trace-pg-cascade";
        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-casc-1", Instant.now(), "order-service", null, EventType.REQUEST, Severity.INFO,
                traceId, null, null, "Msg", Map.of()
        ));

        var sc = scenarioService.createScenario(traceId);
        String scenarioId = sc.scenario().scenarioId();

        var ver = verificationService.verifyScenario(scenarioId);
        String verificationId = ver.verification().verificationId();

        assertThat(verificationRepository.findByVerificationId(verificationId)).isPresent();

        // Deleting the scenario from the database
        scenarioRepository.deleteById(scenarioRepository.findByScenarioId(scenarioId).orElseThrow().getId());
        scenarioRepository.flush();

        // Verifications must be deleted via cascade
        assertThat(verificationRepository.findByVerificationId(verificationId)).isEmpty();
        assertThat(differenceRepository.findAllByVerificationIdOrderBySequenceNumberAsc(verificationId)).isEmpty();
    }

    @Test
    @DisplayName("3. Database unique constraint on verification_id is enforced by PostgreSQL")
    void uniqueVerificationId_enforced() {
        String traceId = "trace-pg-uniq";
        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-uniq-1", Instant.now(), "service", null, EventType.REQUEST, Severity.INFO,
                traceId, null, null, "Msg", Map.of()
        ));
        String scenarioId = scenarioService.createScenario(traceId).scenario().scenarioId();

        ReplayVerification v1 = new ReplayVerification(
                "verify-duplicate-id", scenarioId, ReplayVerificationStatus.PASSED,
                1, 1, 0, 0, 50L, 50L, 1, 0, 0, 0, 0, 0, "OK"
        );
        verificationRepository.saveAndFlush(v1);

        ReplayVerification v2 = new ReplayVerification(
                "verify-duplicate-id", scenarioId, ReplayVerificationStatus.FAILED,
                1, 1, 0, 0, 50L, 50L, 1, 0, 0, 0, 0, 0, "Fail"
        );
        assertThatThrownBy(() -> verificationRepository.saveAndFlush(v2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("4. End-to-end verification flow: Telemetry -> Replay -> Verify -> PASSED -> Idempotent")
    void endToEnd_verificationFlow() {
        String traceId = "trace-e2e-v5";
        Instant t0 = Instant.parse("2026-09-23T19:00:00.000Z");
        Instant t1 = Instant.parse("2026-09-23T19:00:00.100Z");

        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-e2e-1", t0, "cart-service", "c-1", EventType.REQUEST, Severity.INFO,
                traceId, "req-1", "POST /checkout", "Checkout started", Map.of()
        ));
        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-e2e-2", t1, "payment-service", "p-1", EventType.DATABASE, Severity.ERROR,
                traceId, "req-1", "UPDATE account", "Connection reset", Map.of()
        ));

        // 1. Generate scenario
        ReplayScenarioResponse scenario = scenarioService.createScenario(traceId).scenario();
        String scenarioId = scenario.scenarioId();

        // 2. Execute simulation
        executionService.executeScenario(scenarioId);

        // 3. Verify scenario
        var verifyResult = verificationService.verifyScenario(scenarioId);
        assertThat(verifyResult.newlyCreated()).isTrue();
        ReplayVerificationResponse resp = verifyResult.verification();

        assertThat(resp.status()).isEqualTo(ReplayVerificationStatus.PASSED);
        assertThat(resp.eventsMatched()).isEqualTo(2);
        assertThat(resp.originalFailureCount()).isEqualTo(1);
        assertThat(resp.replayedFailureCount()).isEqualTo(1);
        assertThat(resp.differences()).isEmpty();

        // 4. Repeated verification is idempotent
        var repeatResult = verificationService.verifyScenario(scenarioId);
        assertThat(repeatResult.newlyCreated()).isFalse();
        assertThat(repeatResult.verification().verificationId()).isEqualTo(resp.verificationId());

        // 5. Query verification by ID
        ReplayVerificationResponse retrieved = verificationService.getVerification(resp.verificationId());
        assertThat(retrieved.verificationId()).isEqualTo(resp.verificationId());
        assertThat(retrieved.status()).isEqualTo(ReplayVerificationStatus.PASSED);

        // 6. Query verifications for scenario
        List<ReplayVerificationResponse> scenarioVerifications = verificationService.getVerificationsForScenario(scenarioId);
        assertThat(scenarioVerifications).hasSize(1);
        assertThat(scenarioVerifications.get(0).verificationId()).isEqualTo(resp.verificationId());
    }

    @Test
    @DisplayName("5. Custom observations detecting FAILED replay with differences persisted in PostgreSQL")
    void customObservations_persistsDifferencesInPostgreSQL() {
        String traceId = "trace-diff-v5";
        Instant t0 = Instant.parse("2026-09-23T20:00:00.000Z");
        Instant t1 = Instant.parse("2026-09-23T20:00:00.050Z");

        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-d-1", t0, "order-service", null, EventType.REQUEST, Severity.INFO,
                traceId, "req-d", "POST /orders", "Order", Map.of()
        ));
        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-d-2", t1, "inventory-service", null, EventType.DATABASE, Severity.ERROR,
                traceId, "req-d", "RESERVE item", "Lock timeout", Map.of()
        ));

        String scenarioId = scenarioService.createScenario(traceId).scenario().scenarioId();

        // Observed simulation where inventory-service succeeded (WARN instead of ERROR), and service was billing-service
        List<ReplayObservedEvent> customObservations = List.of(
                new ReplayObservedEvent(1, "evt-d-1", "order-service", EventType.REQUEST, Severity.INFO, 0L),
                new ReplayObservedEvent(2, "evt-d-2", "billing-service", EventType.DATABASE, Severity.WARN, 50L)
        );

        var result = verificationService.verifyScenarioWithObservations(scenarioId, customObservations, 50L);
        ReplayVerificationResponse resp = result.verification();

        assertThat(resp.status()).isEqualTo(ReplayVerificationStatus.FAILED);
        assertThat(resp.serviceMismatches()).isEqualTo(1);
        assertThat(resp.severityMismatches()).isEqualTo(1);
        assertThat(resp.differences()).hasSize(2);

        // Verify differences are in PostgreSQL
        List<ReplayVerificationDifference> diffsInDb = differenceRepository
                .findAllByVerificationIdOrderBySequenceNumberAsc(resp.verificationId());
        assertThat(diffsInDb).hasSize(2);
        assertThat(diffsInDb).extracting(ReplayVerificationDifference::getDifferenceType)
                .containsExactlyInAnyOrder(
                        VerificationDifferenceType.SERVICE_MISMATCH,
                        VerificationDifferenceType.SEVERITY_MISMATCH
                );
    }

    @Test
    @DisplayName("6. Unknown verification or scenario throws ResourceNotFoundException")
    void unknownVerification_throwsResourceNotFoundException() {
        assertThatThrownBy(() -> verificationService.getVerification("verify-nonexistent"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Verification not found with id: verify-nonexistent");

        assertThatThrownBy(() -> verificationService.getVerificationsForScenario("scen-nonexistent"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Scenario not found with id: scen-nonexistent");
    }
}
