package com.chaosreplay.integration;

import com.chaosreplay.api.dto.CreateTelemetryEventRequest;
import com.chaosreplay.api.dto.FailureAnalysisResponse;
import com.chaosreplay.api.dto.ReplayScenarioResponse;
import com.chaosreplay.api.dto.ReplayVerificationResponse;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.FailureAnalysis;
import com.chaosreplay.domain.FailureAnalysisCandidate;
import com.chaosreplay.domain.FailureAnalysisConclusion;
import com.chaosreplay.domain.FailureAnalysisEvidence;
import com.chaosreplay.domain.FailureAnalysisStatus;
import com.chaosreplay.domain.FailureCandidateType;
import com.chaosreplay.domain.FailureEvidenceType;
import com.chaosreplay.domain.Severity;
import com.chaosreplay.exception.ResourceNotFoundException;
import com.chaosreplay.repository.FailureAnalysisCandidateRepository;
import com.chaosreplay.repository.FailureAnalysisEvidenceRepository;
import com.chaosreplay.repository.FailureAnalysisRepository;
import com.chaosreplay.repository.ReplayScenarioEventRepository;
import com.chaosreplay.repository.ReplayScenarioRepository;
import com.chaosreplay.repository.ReplayVerificationDifferenceRepository;
import com.chaosreplay.repository.ReplayVerificationRepository;
import com.chaosreplay.repository.TelemetryEventRepository;
import com.chaosreplay.service.FailureAnalysisService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real PostgreSQL integration test utilizing Testcontainers for Step 6:
 * Deterministic Failure Analysis & Root-Cause Evidence.
 * <p>
 * Verifies V4 Flyway migration, failure analysis persistence, candidate ranking,
 * evidence attachment, foreign-key cascade deletion, unique constraints, and end-to-end flow.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class FailureAnalysisPostgresIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(FailureAnalysisPostgresIntegrationTest.class);

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
    private FailureAnalysisRepository analysisRepository;

    @Autowired
    private FailureAnalysisCandidateRepository candidateRepository;

    @Autowired
    private FailureAnalysisEvidenceRepository evidenceRepository;

    @Autowired
    private TelemetryService telemetryService;

    @Autowired
    private ReplayScenarioService scenarioService;

    @Autowired
    private ReplayExecutionService executionService;

    @Autowired
    private ReplayVerificationService verificationService;

    @Autowired
    private FailureAnalysisService analysisService;

    @BeforeEach
    void setUp() {
        log.info("==== VERIFIED: Testcontainers PostgreSQL 16 is active at [{}] ====", postgres.getJdbcUrl());
        evidenceRepository.deleteAll();
        candidateRepository.deleteAll();
        analysisRepository.deleteAll();
        differenceRepository.deleteAll();
        verificationRepository.deleteAll();
        scenarioEventRepository.deleteAll();
        scenarioRepository.deleteAll();
        telemetryEventRepository.deleteAll();
    }

    @Test
    @DisplayName("1. Flyway V4 migration succeeds and persists FailureAnalysis, Candidate, and Evidence")
    void flywayV4_persistsAnalysisCandidateAndEvidence() {
        FailureAnalysis analysis = new FailureAnalysis(
                "analysis-pg-test-01",
                "trace-pg-01",
                null,
                null,
                FailureAnalysisStatus.COMPLETED,
                FailureAnalysisConclusion.ROOT_CAUSE_CANDIDATE,
                new BigDecimal("0.9300"),
                2, 1, 1,
                "payment-service", "evt-1",
                "Summary test"
        );
        analysisRepository.saveAndFlush(analysis);

        FailureAnalysisCandidate candidate = new FailureAnalysisCandidate(
                "analysis-pg-test-01",
                1,
                "payment-service",
                "evt-1",
                "DATABASE",
                "ERROR",
                FailureCandidateType.DATABASE_FAILURE,
                new BigDecimal("0.9300"),
                Instant.now(),
                "Candidate description"
        );
        candidateRepository.saveAndFlush(candidate);

        FailureAnalysisEvidence evidence = new FailureAnalysisEvidence(
                "analysis-pg-test-01",
                1,
                1,
                "evt-1",
                FailureEvidenceType.DATABASE_ERROR,
                "Lock wait timeout"
        );
        evidenceRepository.saveAndFlush(evidence);

        Optional<FailureAnalysis> saved = analysisRepository.findByAnalysisId("analysis-pg-test-01");
        assertThat(saved).isPresent();
        assertThat(saved.get().getPrimaryServiceName()).isEqualTo("payment-service");

        List<FailureAnalysisCandidate> savedCandidates = candidateRepository
                .findAllByAnalysisIdOrderByRankAsc("analysis-pg-test-01");
        assertThat(savedCandidates).hasSize(1);
        assertThat(savedCandidates.get(0).getCandidateType()).isEqualTo(FailureCandidateType.DATABASE_FAILURE);

        List<FailureAnalysisEvidence> savedEvidence = evidenceRepository
                .findAllByAnalysisIdAndCandidateRankOrderBySequenceNumberAsc("analysis-pg-test-01", 1);
        assertThat(savedEvidence).hasSize(1);
        assertThat(savedEvidence.get(0).getEvidenceType()).isEqualTo(FailureEvidenceType.DATABASE_ERROR);
    }

    @Test
    @DisplayName("2. Foreign-key cascade deletion: deleting analysis deletes candidates and evidence")
    void cascadeDelete_analysisDeletesCandidatesAndEvidence() {
        FailureAnalysis analysis = new FailureAnalysis(
                "analysis-cascade-01", "trace-casc", null, null,
                FailureAnalysisStatus.COMPLETED, FailureAnalysisConclusion.ROOT_CAUSE_CANDIDATE,
                new BigDecimal("0.8500"), 1, 1, 1, "svc", "e1", "Summary"
        );
        analysisRepository.saveAndFlush(analysis);

        FailureAnalysisCandidate candidate = new FailureAnalysisCandidate(
                "analysis-cascade-01", 1, "svc", "e1", "ERROR", "ERROR",
                FailureCandidateType.APPLICATION_ERROR, new BigDecimal("0.8500"), Instant.now(), "Desc"
        );
        candidateRepository.saveAndFlush(candidate);

        FailureAnalysisEvidence evidence = new FailureAnalysisEvidence(
                "analysis-cascade-01", 1, 1, "e1", FailureEvidenceType.ERROR_EVENT, "Error"
        );
        evidenceRepository.saveAndFlush(evidence);

        // Delete analysis
        analysisRepository.deleteById(analysis.getId());
        analysisRepository.flush();

        assertThat(candidateRepository.findAllByAnalysisIdOrderByRankAsc("analysis-cascade-01")).isEmpty();
        assertThat(evidenceRepository.findAllByAnalysisIdOrderByCandidateRankAscSequenceNumberAsc("analysis-cascade-01")).isEmpty();
    }

    @Test
    @DisplayName("3. Database unique constraint on (analysis_id, rank) is enforced")
    void uniqueRankConstraint_enforced() {
        FailureAnalysis analysis = new FailureAnalysis(
                "analysis-uniq-rank", "trace-u", null, null,
                FailureAnalysisStatus.COMPLETED, FailureAnalysisConclusion.ROOT_CAUSE_CANDIDATE,
                new BigDecimal("0.7000"), 1, 1, 1, "svc", "e1", "Summary"
        );
        analysisRepository.saveAndFlush(analysis);

        FailureAnalysisCandidate c1 = new FailureAnalysisCandidate(
                "analysis-uniq-rank", 1, "svc1", "e1", "ERROR", "ERROR",
                FailureCandidateType.APPLICATION_ERROR, new BigDecimal("0.7000"), Instant.now(), "Desc"
        );
        candidateRepository.saveAndFlush(c1);

        FailureAnalysisCandidate c2 = new FailureAnalysisCandidate(
                "analysis-uniq-rank", 1, "svc2", "e2", "ERROR", "ERROR",
                FailureCandidateType.SERVICE_FAILURE, new BigDecimal("0.6500"), Instant.now(), "Desc 2"
        );

        assertThatThrownBy(() -> candidateRepository.saveAndFlush(c2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("4. End-to-end pipeline: Telemetry -> Replay -> Verification -> Failure Analysis")
    void endToEnd_failureAnalysisPipeline() {
        String traceId = "trace-e2e-v6";
        Instant t0 = Instant.parse("2026-09-23T11:00:00.000Z");
        Instant t1 = Instant.parse("2026-09-23T11:00:00.040Z");
        Instant t2 = Instant.parse("2026-09-23T11:00:00.120Z");

        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-a-1", t0, "api-gateway", "gw-1", EventType.REQUEST, Severity.INFO,
                traceId, "req-1", "POST /orders", "Incoming order", Map.of()
        ));
        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-a-2", t1, "payment-service", "pay-1", EventType.DATABASE, Severity.ERROR,
                traceId, "req-1", "UPDATE accounts", "Deadlock detected", Map.of("retryCount", 2)
        ));
        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-a-3", t2, "order-service", "ord-1", EventType.REQUEST, Severity.ERROR,
                traceId, "req-1", "POST /orders", "Payment failure propagated", Map.of()
        ));

        // 1. Generate scenario
        ReplayScenarioResponse scenario = scenarioService.createScenario(traceId).scenario();

        // 2. Execute simulation
        executionService.executeScenario(scenario.scenarioId());

        // 3. Verify replay
        ReplayVerificationResponse verification = verificationService.verifyScenario(scenario.scenarioId()).verification();

        // 4. Run failure analysis
        var analysisResult = analysisService.analyzeTrace(traceId);
        assertThat(analysisResult.newlyCreated()).isTrue();

        FailureAnalysisResponse response = analysisResult.response();
        assertThat(response.status()).isEqualTo(FailureAnalysisStatus.COMPLETED);
        assertThat(response.conclusion()).isEqualTo(FailureAnalysisConclusion.ROOT_CAUSE_CANDIDATE);
        assertThat(response.candidateCount()).isEqualTo(2);
        assertThat(response.primaryServiceName()).isEqualTo("payment-service");
        assertThat(response.primaryEventId()).isEqualTo("evt-a-2");
        assertThat(response.scenarioId()).isEqualTo(scenario.scenarioId());
        assertThat(response.verificationId()).isEqualTo(verification.verificationId());

        // Validate Candidate 1 (payment-service DATABASE_FAILURE)
        var cand1 = response.candidates().get(0);
        assertThat(cand1.rank()).isEqualTo(1);
        assertThat(cand1.candidateType()).isEqualTo(FailureCandidateType.DATABASE_FAILURE);
        assertThat(cand1.evidence())
                .extracting(e -> e.evidenceType().name())
                .contains("FIRST_FAILURE", "DATABASE_ERROR", "ERROR_EVENT", "TEMPORAL_PRECEDENCE");

        // Validate Candidate 2 (order-service DOWNSTREAM_FAILURE)
        var cand2 = response.candidates().get(1);
        assertThat(cand2.rank()).isEqualTo(2);
        assertThat(cand2.candidateType()).isEqualTo(FailureCandidateType.DOWNSTREAM_FAILURE);
        assertThat(cand2.evidence())
                .extracting(e -> e.evidenceType().name())
                .contains("DOWNSTREAM_ERROR", "SERVICE_PROPAGATION");

        // 5. Repeated call is idempotent
        var repeatResult = analysisService.analyzeTrace(traceId);
        assertThat(repeatResult.newlyCreated()).isFalse();
        assertThat(repeatResult.response().analysisId()).isEqualTo(response.analysisId());

        // 6. Query by ID
        FailureAnalysisResponse retrieved = analysisService.getAnalysis(response.analysisId());
        assertThat(retrieved.analysisId()).isEqualTo(response.analysisId());

        // 7. Query latest for trace
        FailureAnalysisResponse latest = analysisService.getLatestAnalysisForTrace(traceId);
        assertThat(latest.analysisId()).isEqualTo(response.analysisId());
    }

    @Test
    @DisplayName("5. Clean trace with no failures returns INSUFFICIENT_EVIDENCE / NO_FAILURE_OBSERVED")
    void cleanTrace_returnsNoFailureObserved() {
        String traceId = "trace-clean-v6";
        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                "evt-c-1", Instant.now(), "order-service", null, EventType.REQUEST, Severity.INFO,
                traceId, null, null, "Normal", Map.of()
        ));

        var result = analysisService.analyzeTrace(traceId);
        FailureAnalysisResponse response = result.response();

        assertThat(response.status()).isEqualTo(FailureAnalysisStatus.INSUFFICIENT_EVIDENCE);
        assertThat(response.conclusion()).isEqualTo(FailureAnalysisConclusion.NO_FAILURE_OBSERVED);
        assertThat(response.candidateCount()).isZero();
        assertThat(response.confidenceScore()).isEqualTo(new BigDecimal("0.0000"));
    }

    @Test
    @DisplayName("6. Unknown trace throws ResourceNotFoundException")
    void unknownTrace_throwsResourceNotFoundException() {
        assertThatThrownBy(() -> analysisService.analyzeTrace("trace-nonexistent"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Trace not found: trace-nonexistent");
    }
}

