package com.chaosreplay.integration;

import com.chaosreplay.api.dto.CreateTelemetryEventRequest;
import com.chaosreplay.api.dto.FailureAnalysisResponse;
import com.chaosreplay.api.dto.IncidentInvestigationResponse;
import com.chaosreplay.api.dto.IncidentTimelineResponse;
import com.chaosreplay.api.dto.ReplayScenarioResponse;
import com.chaosreplay.api.dto.ReplayVerificationResponse;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.FailureAnalysisConclusion;
import com.chaosreplay.domain.FailureAnalysisStatus;
import com.chaosreplay.domain.FailureCandidateType;
import com.chaosreplay.domain.IncidentEntryCategory;
import com.chaosreplay.domain.ReplayScenarioStatus;
import com.chaosreplay.domain.ReplayVerificationStatus;
import com.chaosreplay.domain.Severity;
import com.chaosreplay.repository.FailureAnalysisCandidateRepository;
import com.chaosreplay.repository.FailureAnalysisEvidenceRepository;
import com.chaosreplay.repository.FailureAnalysisRepository;
import com.chaosreplay.repository.ReplayScenarioEventRepository;
import com.chaosreplay.repository.ReplayScenarioRepository;
import com.chaosreplay.repository.ReplayVerificationDifferenceRepository;
import com.chaosreplay.repository.ReplayVerificationRepository;
import com.chaosreplay.repository.TelemetryEventRepository;
import com.chaosreplay.service.FailureAnalysisService;
import com.chaosreplay.service.IncidentInvestigationService;
import com.chaosreplay.service.IncidentTimelineService;
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
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real PostgreSQL integration test utilizing Testcontainers for Step 7:
 * Incident Timeline & Explainable Failure Insights.
 * <p>
 * Validates real PostgreSQL queries against migrated schemas, timeline chronological ordering and relative offsets,
 * candidate and evidence associations, optional component behavior, read-only guarantees (zero row count changes),
 * and end-to-end integration across telemetry, replay scenario, verification, and failure analysis.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class IncidentPostgresIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(IncidentPostgresIntegrationTest.class);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TelemetryEventRepository telemetryEventRepository;

    @Autowired
    private ReplayScenarioRepository replayScenarioRepository;

    @Autowired
    private ReplayScenarioEventRepository replayScenarioEventRepository;

    @Autowired
    private ReplayVerificationRepository replayVerificationRepository;

    @Autowired
    private ReplayVerificationDifferenceRepository differenceRepository;

    @Autowired
    private FailureAnalysisRepository failureAnalysisRepository;

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
    private FailureAnalysisService failureAnalysisService;

    @Autowired
    private IncidentTimelineService timelineService;

    @Autowired
    private IncidentInvestigationService investigationService;

    @BeforeEach
    void setUp() {
        evidenceRepository.deleteAll();
        candidateRepository.deleteAll();
        failureAnalysisRepository.deleteAll();
        differenceRepository.deleteAll();
        replayVerificationRepository.deleteAll();
        replayScenarioEventRepository.deleteAll();
        replayScenarioRepository.deleteAll();
        telemetryEventRepository.deleteAll();
        log.info("==== VERIFIED: Testcontainers PostgreSQL 16 is active at [{}] ====", postgres.getJdbcUrl());
    }

    private void ingestEvent(
            String eventId,
            Instant timestamp,
            String serviceName,
            EventType eventType,
            Severity severity,
            String traceId,
            String operation,
            String message,
            Map<String, Object> metadata
    ) {
        telemetryService.ingestEvent(new CreateTelemetryEventRequest(
                eventId,
                timestamp,
                serviceName,
                "inst-1",
                eventType,
                severity,
                traceId,
                null,
                operation,
                message,
                metadata
        ));
    }

    @Test
    @DisplayName("1. Real PostgreSQL: Timeline ordering and relative offsets computed accurately")
    void timelineOrderingAndOffsets_againstRealPostgres() {
        String traceId = "trace-pg-timeline-01";
        Instant t0 = Instant.parse("2026-10-18T10:00:00.000Z");
        Instant t1 = Instant.parse("2026-10-18T10:00:00.080Z");
        Instant t2 = Instant.parse("2026-10-18T10:00:00.200Z");

        // Ingest in non-chronological order to verify DB ordering query
        ingestEvent("evt-t2", t2, "notification-service", EventType.REQUEST, Severity.INFO, traceId, "POST /send", "Notification sent", null);
        ingestEvent("evt-t0", t0, "api-gateway", EventType.REQUEST, Severity.INFO, traceId, "POST /checkout", "Ingress request", null);
        ingestEvent("evt-t1", t1, "payment-service", EventType.DATABASE, Severity.ERROR, traceId, "UPDATE balance", "Connection timeout", Map.of("timeoutMs", 3000));

        IncidentTimelineResponse response = timelineService.getTimeline(traceId);

        assertThat(response.traceId()).isEqualTo(traceId);
        assertThat(response.eventCount()).isEqualTo(3);
        assertThat(response.failureCount()).isEqualTo(1);
        assertThat(response.failureObserved()).isTrue();
        assertThat(response.durationMs()).isEqualTo(200L);

        // Verify sorted order
        assertThat(response.timeline().get(0).eventId()).isEqualTo("evt-t0");
        assertThat(response.timeline().get(0).offsetMs()).isEqualTo(0L);
        assertThat(response.timeline().get(0).entryCategory()).isEqualTo(IncidentEntryCategory.NORMAL_EVENT);

        assertThat(response.timeline().get(1).eventId()).isEqualTo("evt-t1");
        assertThat(response.timeline().get(1).offsetMs()).isEqualTo(80L);
        assertThat(response.timeline().get(1).entryCategory()).isEqualTo(IncidentEntryCategory.DATABASE_FAILURE);
        assertThat(response.timeline().get(1).failureObserved()).isTrue();

        assertThat(response.timeline().get(2).eventId()).isEqualTo("evt-t2");
        assertThat(response.timeline().get(2).offsetMs()).isEqualTo(200L);
        assertThat(response.timeline().get(2).entryCategory()).isEqualTo(IncidentEntryCategory.NORMAL_EVENT);
    }

    @Test
    @DisplayName("2. Real PostgreSQL: Optional data behavior when scenario, verification, or analysis is absent")
    void optionalDataBehavior_whenComponentsAbsent() {
        String traceId = "trace-pg-opt-01";
        Instant t0 = Instant.parse("2026-10-18T10:00:00.000Z");

        ingestEvent("evt-1", t0, "order-service", EventType.DATABASE, Severity.ERROR, traceId, "SELECT item", "Deadlock", null);

        IncidentInvestigationResponse response = investigationService.getInvestigation(traceId);

        assertThat(response.traceId()).isEqualTo(traceId);
        assertThat(response.correlationAvailable()).isTrue();
        assertThat(response.scenarioAvailable()).isFalse();
        assertThat(response.scenarioId()).isNull();
        assertThat(response.executionAvailable()).isFalse();
        assertThat(response.verificationAvailable()).isFalse();
        assertThat(response.analysisAvailable()).isFalse();
        assertThat(response.candidates()).isEmpty();
        assertThat(response.limitations()).isNotEmpty();
        assertThat(response.limitations())
                .anyMatch(l -> l.contains("Failure analysis has not been performed"))
                .anyMatch(l -> l.contains("Replay scenario has not been generated"));
    }

    @Test
    @DisplayName("3. Real PostgreSQL: Clean trace returns NO_FAILURE_OBSERVED and INSUFFICIENT_EVIDENCE")
    void cleanTrace_reportsNoFailureObserved_againstRealPostgres() {
        String traceId = "trace-pg-clean-01";
        Instant t0 = Instant.parse("2026-10-18T10:00:00.000Z");

        ingestEvent("evt-c1", t0, "order-service", EventType.REQUEST, Severity.INFO, traceId, "GET /order", "Success", null);

        IncidentTimelineResponse timeline = timelineService.getTimeline(traceId);
        assertThat(timeline.failureObserved()).isFalse();
        assertThat(timeline.failureCount()).isZero();
        assertThat(timeline.rootCauseStatus()).isEqualTo(FailureAnalysisStatus.INSUFFICIENT_EVIDENCE);
        assertThat(timeline.conclusion()).isEqualTo(FailureAnalysisConclusion.NO_FAILURE_OBSERVED);

        IncidentInvestigationResponse investigation = investigationService.getInvestigation(traceId);
        assertThat(investigation.analysisStatus()).isEqualTo(FailureAnalysisStatus.INSUFFICIENT_EVIDENCE);
        assertThat(investigation.conclusion()).isEqualTo(FailureAnalysisConclusion.NO_FAILURE_OBSERVED);
        assertThat(investigation.limitations())
                .anyMatch(l -> l.contains("No failure events were observed"));
    }

    @Test
    @DisplayName("4. Real PostgreSQL: Read-only guarantee: reading timeline and investigation causes zero row count changes")
    void readOnlyBehavior_databaseRowCountsUnchanged() {
        String traceId = "trace-pg-ro-01";
        Instant t0 = Instant.parse("2026-10-18T10:00:00.000Z");
        Instant t1 = Instant.parse("2026-10-18T10:00:00.050Z");

        ingestEvent("evt-ro-1", t0, "api-gateway", EventType.REQUEST, Severity.INFO, traceId, "POST /pay", "Start", null);
        ingestEvent("evt-ro-2", t1, "payment-service", EventType.DATABASE, Severity.ERROR, traceId, "UPDATE balance", "Lock timeout", null);

        ReplayScenarioResponse scenario = scenarioService.createScenario(traceId).scenario();
        executionService.executeScenario(scenario.scenarioId());
        verificationService.verifyScenario(scenario.scenarioId());
        failureAnalysisService.analyzeTrace(traceId);

        // Record counts before read operations
        long telemetryCountBefore = telemetryEventRepository.count();
        long scenarioCountBefore = replayScenarioRepository.count();
        long verificationCountBefore = replayVerificationRepository.count();
        long diffCountBefore = differenceRepository.count();
        long analysisCountBefore = failureAnalysisRepository.count();
        long candidateCountBefore = candidateRepository.count();
        long evidenceCountBefore = evidenceRepository.count();

        // Perform multiple timeline and investigation reads
        for (int i = 0; i < 5; i++) {
            timelineService.getTimeline(traceId);
            investigationService.getInvestigation(traceId);
        }

        // Verify zero row count changes
        assertThat(telemetryEventRepository.count()).isEqualTo(telemetryCountBefore);
        assertThat(replayScenarioRepository.count()).isEqualTo(scenarioCountBefore);
        assertThat(replayVerificationRepository.count()).isEqualTo(verificationCountBefore);
        assertThat(differenceRepository.count()).isEqualTo(diffCountBefore);
        assertThat(failureAnalysisRepository.count()).isEqualTo(analysisCountBefore);
        assertThat(candidateRepository.count()).isEqualTo(candidateCountBefore);
        assertThat(evidenceRepository.count()).isEqualTo(evidenceCountBefore);
    }

    @Test
    @DisplayName("5. Real PostgreSQL: End-to-end integration across all lifecycle stages")
    void endToEndPipeline_allStagesIntegrated() {
        String traceId = "trace-pg-e2e-01";
        Instant t0 = Instant.parse("2026-10-18T10:00:00.000Z");
        Instant t1 = Instant.parse("2026-10-18T10:00:00.040Z");
        Instant t2 = Instant.parse("2026-10-18T10:00:00.100Z");

        // 1. Ingest telemetry
        ingestEvent("evt-e1", t0, "api-gateway", EventType.REQUEST, Severity.INFO, traceId, "POST /order", "Start", null);
        ingestEvent("evt-e2", t1, "payment-service", EventType.DATABASE, Severity.ERROR, traceId, "UPDATE balance", "Deadlock", Map.of("retryCount", 3));
        ingestEvent("evt-e3", t2, "order-service", EventType.REQUEST, Severity.ERROR, traceId, "POST /order", "Payment failure propagated", null);

        // 2. Generate scenario
        ReplayScenarioResponse scenarioResp = scenarioService.createScenario(traceId).scenario();

        // 3. Execute simulation
        executionService.executeScenario(scenarioResp.scenarioId());

        // 4. Verify replay
        ReplayVerificationResponse verifResp = verificationService.verifyScenario(scenarioResp.scenarioId()).verification();

        // 5. Generate failure analysis
        FailureAnalysisResponse analysisResp = failureAnalysisService.analyzeTrace(traceId).response();

        // 6. Retrieve Incident Timeline
        IncidentTimelineResponse timeline = timelineService.getTimeline(traceId);
        assertThat(timeline.analysisAvailable()).isTrue();
        assertThat(timeline.verificationAvailable()).isTrue();
        assertThat(timeline.primaryServiceName()).isEqualTo("payment-service");
        assertThat(timeline.primaryEventId()).isEqualTo("evt-e2");
        assertThat(timeline.conclusion()).isEqualTo(FailureAnalysisConclusion.ROOT_CAUSE_CANDIDATE);
        assertThat(timeline.timeline()).hasSize(3);

        // Check entry categories and propagation
        assertThat(timeline.timeline().get(0).entryCategory()).isEqualTo(IncidentEntryCategory.NORMAL_EVENT);
        assertThat(timeline.timeline().get(1).entryCategory()).isEqualTo(IncidentEntryCategory.DATABASE_FAILURE);
        assertThat(timeline.timeline().get(1).relatedCandidateRank()).isEqualTo(1);
        assertThat(timeline.timeline().get(1).evidenceReferences()).isNotEmpty();

        assertThat(timeline.timeline().get(2).entryCategory()).isEqualTo(IncidentEntryCategory.DOWNSTREAM_ERROR);
        assertThat(timeline.timeline().get(2).failurePropagation()).isTrue();

        // 7. Retrieve Consolidated Investigation
        IncidentInvestigationResponse investigation = investigationService.getInvestigation(traceId);
        assertThat(investigation.correlationAvailable()).isTrue();
        assertThat(investigation.scenarioAvailable()).isTrue();
        assertThat(investigation.executionAvailable()).isTrue();
        assertThat(investigation.executionStatus()).isEqualTo(ReplayScenarioStatus.COMPLETED);
        assertThat(investigation.verificationAvailable()).isTrue();
        assertThat(investigation.verificationStatus()).isEqualTo(ReplayVerificationStatus.PASSED);
        assertThat(investigation.analysisAvailable()).isTrue();
        assertThat(investigation.analysisId()).isEqualTo(analysisResp.analysisId());
        assertThat(investigation.candidates()).hasSize(2);
        assertThat(investigation.evidenceSummary()).isNotEmpty();
        assertThat(investigation.limitations()).isNotEmpty();
    }

    @Test
    @DisplayName("6. Real PostgreSQL: 800ms database timeout scenario with subsequent independent error - not falsely blamed")
    void databaseTimeout800ms_withIndependentSubsequentError_notFalselyBlamed() {
        String traceId = "trace-pg-800ms-indep";
        Instant t0 = Instant.parse("2026-10-18T10:00:00.000Z");
        Instant t1 = Instant.parse("2026-10-18T10:00:00.080Z");
        Instant t2 = Instant.parse("2026-10-18T10:00:00.800Z"); // +800ms database timeout
        Instant t3 = Instant.parse("2026-10-18T10:00:00.860Z"); // +860ms independent failure

        // 1. Ingest telemetry matching the original 800ms incident
        ingestEvent("evt-1", t0, "order-service", EventType.REQUEST, Severity.INFO, traceId,
                "POST /api/orders", "Order placement initiated", Map.of("cartId", "cart-1"));
        ingestEvent("evt-2", t1, "inventory-service", EventType.EXTERNAL_CALL, Severity.INFO, traceId,
                "reserveStock", "Reserving stock for SKU catalog", Map.of("sku", "SKU-9901"));
        ingestEvent("evt-3", t2, "inventory-service", EventType.DATABASE, Severity.FATAL, traceId,
                "UPDATE inventory", "Database query timed out after 3000ms: Lock wait timeout exceeded",
                Map.of("table", "inventory_locks", "timeoutMs", 3000));
        // Independent error in notification-service with no propagation evidence
        ingestEvent("evt-4", t3, "notification-service", EventType.REQUEST, Severity.ERROR, traceId,
                "POST /alerts", "Email provider authentication failed: invalid api token",
                Map.of("authError", true));

        // 2. Perform full failure analysis
        FailureAnalysisResponse analysisResp = failureAnalysisService.analyzeTrace(traceId).response();

        // 3. Inspect timeline
        IncidentTimelineResponse timeline = timelineService.getTimeline(traceId);
        assertThat(timeline.eventCount()).isEqualTo(4);
        assertThat(timeline.failureCount()).isEqualTo(2);
        assertThat(timeline.durationMs()).isEqualTo(860L);

        // Assert database timeout event: accurately reported at +800ms, FATAL severity, DATABASE_FAILURE
        var dbEntry = timeline.timeline().get(2);
        assertThat(dbEntry.eventId()).isEqualTo("evt-3");
        assertThat(dbEntry.offsetMs()).isEqualTo(800L);
        assertThat(dbEntry.serviceName()).isEqualTo("inventory-service");
        assertThat(dbEntry.entryCategory()).isEqualTo(IncidentEntryCategory.DATABASE_FAILURE);
        assertThat(dbEntry.failureObserved()).isTrue();
        assertThat(dbEntry.failurePropagation()).isFalse();

        // Assert subsequent independent error at +860ms: NOT classified as downstream failure
        var indepEntry = timeline.timeline().get(3);
        assertThat(indepEntry.eventId()).isEqualTo("evt-4");
        assertThat(indepEntry.offsetMs()).isEqualTo(860L);
        assertThat(indepEntry.serviceName()).isEqualTo("notification-service");
        assertThat(indepEntry.entryCategory()).isEqualTo(IncidentEntryCategory.ERROR_EVENT);
        assertThat(indepEntry.failurePropagation()).isFalse(); // CRITICAL: Not falsely marked as propagation

        // Assert analysis candidate classification
        var indepCandidate = analysisResp.candidates().stream()
                .filter(c -> c.eventId().equals("evt-4"))
                .findFirst()
                .orElseThrow();
        assertThat(indepCandidate.candidateType()).isNotEqualTo(FailureCandidateType.DOWNSTREAM_FAILURE);

        // 4. Inspect consolidated investigation
        IncidentInvestigationResponse investigation = investigationService.getInvestigation(traceId);
        assertThat(investigation.timeline().timeline().get(2).offsetMs()).isEqualTo(800L);
        assertThat(investigation.limitations())
                .anyMatch(l -> l.contains("clock skew") || l.contains("relative offsets"));
    }

    @Test
    @DisplayName("7. Real PostgreSQL: 800ms database timeout scenario with explicit propagation - correctly classified downstream")
    void databaseTimeout800ms_withExplicitPropagation_correctlyClassified() {
        String traceId = "trace-pg-800ms-prop";
        Instant t0 = Instant.parse("2026-10-18T10:00:00.000Z");
        Instant t1 = Instant.parse("2026-10-18T10:00:00.080Z");
        Instant t2 = Instant.parse("2026-10-18T10:00:00.800Z"); // +800ms database timeout
        Instant t3 = Instant.parse("2026-10-18T10:00:00.860Z"); // +860ms downstream error

        // 1. Ingest telemetry matching the original 800ms incident
        ingestEvent("evt-1", t0, "order-service", EventType.REQUEST, Severity.INFO, traceId,
                "POST /api/orders", "Order placement initiated", Map.of("cartId", "cart-1"));
        ingestEvent("evt-2", t1, "inventory-service", EventType.EXTERNAL_CALL, Severity.INFO, traceId,
                "reserveStock", "Reserving stock for SKU catalog", Map.of("sku", "SKU-9901"));
        ingestEvent("evt-3", t2, "inventory-service", EventType.DATABASE, Severity.FATAL, traceId,
                "UPDATE inventory", "Database query timed out after 3000ms: Lock wait timeout exceeded",
                Map.of("table", "inventory_locks", "timeoutMs", 3000));
        // Explicit propagation error from order-service caused by inventory database failure
        ingestEvent("evt-4", t3, "order-service", EventType.RESPONSE, Severity.ERROR, traceId,
                "POST /api/orders", "Order placement failed with downstream database error 500: inventory-service timeout",
                Map.of("httpStatus", 500));

        // 2. Perform full failure analysis
        FailureAnalysisResponse analysisResp = failureAnalysisService.analyzeTrace(traceId).response();

        // 3. Inspect timeline
        IncidentTimelineResponse timeline = timelineService.getTimeline(traceId);
        assertThat(timeline.eventCount()).isEqualTo(4);
        assertThat(timeline.failureCount()).isEqualTo(2);

        // Database timeout remains root cause candidate at +800ms
        assertThat(timeline.primaryServiceName()).isEqualTo("inventory-service");
        assertThat(timeline.primaryEventId()).isEqualTo("evt-3");
        var dbEntry = timeline.timeline().get(2);
        assertThat(dbEntry.offsetMs()).isEqualTo(800L);
        assertThat(dbEntry.entryCategory()).isEqualTo(IncidentEntryCategory.DATABASE_FAILURE);
        assertThat(dbEntry.failurePropagation()).isFalse();

        // Subsequent failure with explicit evidence IS classified as DOWNSTREAM_ERROR
        var propEntry = timeline.timeline().get(3);
        assertThat(propEntry.eventId()).isEqualTo("evt-4");
        assertThat(propEntry.offsetMs()).isEqualTo(860L);
        assertThat(propEntry.entryCategory()).isEqualTo(IncidentEntryCategory.DOWNSTREAM_ERROR);
        assertThat(propEntry.failurePropagation()).isTrue();

        // Top candidate in analysis is the database timeout
        assertThat(analysisResp.candidates().get(0).candidateType()).isEqualTo(FailureCandidateType.DATABASE_FAILURE);
        assertThat(analysisResp.candidates().get(0).eventId()).isEqualTo("evt-3");

        // Second candidate is downstream failure
        assertThat(analysisResp.candidates().get(1).candidateType()).isEqualTo(FailureCandidateType.DOWNSTREAM_FAILURE);
        assertThat(analysisResp.candidates().get(1).eventId()).isEqualTo("evt-4");
    }
}
