package com.chaosreplay.service;

import com.chaosreplay.api.dto.IncidentTimelineResponse;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.FailureAnalysis;
import com.chaosreplay.domain.FailureAnalysisCandidate;
import com.chaosreplay.domain.FailureAnalysisConclusion;
import com.chaosreplay.domain.FailureAnalysisEvidence;
import com.chaosreplay.domain.FailureAnalysisStatus;
import com.chaosreplay.domain.FailureCandidateType;
import com.chaosreplay.domain.FailureEvidenceType;
import com.chaosreplay.domain.IncidentEntryCategory;
import com.chaosreplay.domain.ReplayScenario;
import com.chaosreplay.domain.ReplayScenarioStatus;
import com.chaosreplay.domain.ReplayVerification;
import com.chaosreplay.domain.ReplayVerificationDifference;
import com.chaosreplay.domain.ReplayVerificationStatus;
import com.chaosreplay.domain.Severity;
import com.chaosreplay.domain.TelemetryEvent;
import com.chaosreplay.domain.VerificationDifferenceType;
import com.chaosreplay.exception.ResourceNotFoundException;
import com.chaosreplay.repository.FailureAnalysisCandidateRepository;
import com.chaosreplay.repository.FailureAnalysisEvidenceRepository;
import com.chaosreplay.repository.FailureAnalysisRepository;
import com.chaosreplay.repository.ReplayScenarioRepository;
import com.chaosreplay.repository.ReplayVerificationDifferenceRepository;
import com.chaosreplay.repository.ReplayVerificationRepository;
import com.chaosreplay.repository.TelemetryEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentTimelineServiceTest {

    @Mock
    private TelemetryEventRepository telemetryRepository;
    @Mock
    private FailureAnalysisRepository analysisRepository;
    @Mock
    private FailureAnalysisCandidateRepository candidateRepository;
    @Mock
    private FailureAnalysisEvidenceRepository evidenceRepository;
    @Mock
    private ReplayScenarioRepository scenarioRepository;
    @Mock
    private ReplayVerificationRepository verificationRepository;
    @Mock
    private ReplayVerificationDifferenceRepository differenceRepository;

    private IncidentTimelineService timelineService;

    @BeforeEach
    void setUp() {
        timelineService = new IncidentTimelineService(
                telemetryRepository,
                analysisRepository,
                candidateRepository,
                evidenceRepository,
                scenarioRepository,
                verificationRepository,
                differenceRepository
        );
    }

    private TelemetryEvent createEvent(
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
        return new TelemetryEvent(
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
        );
    }

    @Test
    @DisplayName("1. Events are ordered chronologically by timestamp ascending")
    void chronologicalOrdering_ordersByTimestampAsc() {
        String traceId = "trace-chrono-01";
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");
        Instant t1 = Instant.parse("2026-10-18T10:00:00.050Z");
        Instant t2 = Instant.parse("2026-10-18T10:00:00.120Z");

        List<TelemetryEvent> events = List.of(
                createEvent("evt-1", t0, "api-gateway", EventType.REQUEST, Severity.INFO, traceId, "GET /order", "Start", Map.of()),
                createEvent("evt-2", t1, "order-service", EventType.REQUEST, Severity.INFO, traceId, "validate", "Validating", Map.of()),
                createEvent("evt-3", t2, "payment-service", EventType.DATABASE, Severity.ERROR, traceId, "UPDATE balance", "Timeout", Map.of())
        );

        when(telemetryRepository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId)).thenReturn(events);
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());

        IncidentTimelineResponse response = timelineService.getTimeline(traceId);

        assertThat(response.timeline()).hasSize(3);
        assertThat(response.timeline().get(0).eventId()).isEqualTo("evt-1");
        assertThat(response.timeline().get(1).eventId()).isEqualTo("evt-2");
        assertThat(response.timeline().get(2).eventId()).isEqualTo("evt-3");
    }

    @Test
    @DisplayName("2. Deterministic event ID tie-breaker is applied for equal timestamps")
    void eventIdTieBreaker_ordersByEventIdAsc() {
        String traceId = "trace-tie-01";
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");

        List<TelemetryEvent> events = List.of(
                createEvent("evt-b", t0, "svc-b", EventType.REQUEST, Severity.INFO, traceId, "op", "Msg", Map.of()),
                createEvent("evt-a", t0, "svc-a", EventType.REQUEST, Severity.INFO, traceId, "op", "Msg", Map.of())
        );

        // Telemetry repository query returns ordered by timestamp ASC, eventId ASC
        when(telemetryRepository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId)).thenReturn(List.of(events.get(1), events.get(0)));
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());

        IncidentTimelineResponse response = timelineService.getTimeline(traceId);

        assertThat(response.timeline().get(0).eventId()).isEqualTo("evt-a");
        assertThat(response.timeline().get(1).eventId()).isEqualTo("evt-b");
    }

    @Test
    @DisplayName("3. Relative offsets are calculated correctly from earliest event (0 ms baseline)")
    void relativeOffsets_computedFromEarliestEvent() {
        String traceId = "trace-offset-01";
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");
        Instant t1 = Instant.parse("2026-10-18T10:00:00.045Z");
        Instant t2 = Instant.parse("2026-10-18T10:00:00.150Z");

        List<TelemetryEvent> events = List.of(
                createEvent("evt-1", t0, "api-gateway", EventType.REQUEST, Severity.INFO, traceId, "GET /order", "Start", Map.of()),
                createEvent("evt-2", t1, "order-service", EventType.REQUEST, Severity.INFO, traceId, "validate", "Validating", Map.of()),
                createEvent("evt-3", t2, "payment-service", EventType.DATABASE, Severity.ERROR, traceId, "pay", "Err", Map.of())
        );

        when(telemetryRepository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId)).thenReturn(events);
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());

        IncidentTimelineResponse response = timelineService.getTimeline(traceId);

        assertThat(response.timeline().get(0).offsetMs()).isEqualTo(0L);
        assertThat(response.timeline().get(1).offsetMs()).isEqualTo(45L);
        assertThat(response.timeline().get(2).offsetMs()).isEqualTo(150L);
        assertThat(response.durationMs()).isEqualTo(150L);
    }

    @Test
    @DisplayName("4. Event and failure counts match single-counted failure predicate")
    void eventAndFailureCounts_matchPredicate() {
        String traceId = "trace-counts-01";
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");
        Instant t1 = Instant.parse("2026-10-18T10:00:00.010Z");
        Instant t2 = Instant.parse("2026-10-18T10:00:00.020Z");

        List<TelemetryEvent> events = List.of(
                createEvent("evt-1", t0, "gateway", EventType.REQUEST, Severity.INFO, traceId, "op1", "OK", Map.of()),
                // Matching multiple: eventType == ERROR AND severity == ERROR -> single counted failure
                createEvent("evt-2", t1, "order-service", EventType.ERROR, Severity.ERROR, traceId, "op2", "Crash", Map.of()),
                createEvent("evt-3", t2, "payment-service", EventType.DATABASE, Severity.FATAL, traceId, "op3", "Fatal DB", Map.of())
        );

        when(telemetryRepository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId)).thenReturn(events);
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());

        IncidentTimelineResponse response = timelineService.getTimeline(traceId);

        assertThat(response.eventCount()).isEqualTo(3);
        assertThat(response.failureCount()).isEqualTo(2);
        assertThat(response.failureObserved()).isTrue();
    }

    @Test
    @DisplayName("5. Clean trace with zero failures returns INSUFFICIENT_EVIDENCE and NO_FAILURE_OBSERVED")
    void cleanTrace_returnsNoFailureObserved() {
        String traceId = "trace-clean-01";
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");

        List<TelemetryEvent> events = List.of(
                createEvent("evt-1", t0, "gateway", EventType.REQUEST, Severity.INFO, traceId, "GET /health", "OK", Map.of())
        );

        when(telemetryRepository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId)).thenReturn(events);
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());

        IncidentTimelineResponse response = timelineService.getTimeline(traceId);

        assertThat(response.failureObserved()).isFalse();
        assertThat(response.failureCount()).isZero();
        assertThat(response.rootCauseStatus()).isEqualTo(FailureAnalysisStatus.INSUFFICIENT_EVIDENCE);
        assertThat(response.conclusion()).isEqualTo(FailureAnalysisConclusion.NO_FAILURE_OBSERVED);
        assertThat(response.confidenceScore()).isEqualTo(new BigDecimal("0.0000"));
        assertThat(response.timeline().get(0).entryCategory()).isEqualTo(IncidentEntryCategory.NORMAL_EVENT);
        assertThat(response.timeline().get(0).failureObserved()).isFalse();
    }

    @Test
    @DisplayName("6. Missing or unknown trace throws ResourceNotFoundException")
    void missingTrace_throwsResourceNotFoundException() {
        when(telemetryRepository.findAllByTraceIdOrderByTimestampAscEventIdAsc("missing-trace"))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> timelineService.getTimeline("missing-trace"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Trace not found: missing-trace");
    }

    @Test
    @DisplayName("7. Database failure correctly categorized as DATABASE_FAILURE")
    void databaseFailure_categorizedCorrectly() {
        String traceId = "trace-db-01";
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");

        List<TelemetryEvent> events = List.of(
                createEvent("evt-1", t0, "payment-service", EventType.DATABASE, Severity.ERROR, traceId, "UPDATE balance", "Deadlock", Map.of())
        );

        when(telemetryRepository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId)).thenReturn(events);
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());

        IncidentTimelineResponse response = timelineService.getTimeline(traceId);

        assertThat(response.timeline().get(0).entryCategory()).isEqualTo(IncidentEntryCategory.DATABASE_FAILURE);
        assertThat(response.timeline().get(0).failureObserved()).isTrue();
    }

    @Test
    @DisplayName("8. External call failure correctly categorized as EXTERNAL_CALL_FAILURE")
    void externalCallFailure_categorizedCorrectly() {
        String traceId = "trace-ext-01";
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");

        List<TelemetryEvent> events = List.of(
                createEvent("evt-1", t0, "payment-service", EventType.EXTERNAL_CALL, Severity.FATAL, traceId, "POST /stripe", "Connection refused", Map.of())
        );

        when(telemetryRepository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId)).thenReturn(events);
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());

        IncidentTimelineResponse response = timelineService.getTimeline(traceId);

        assertThat(response.timeline().get(0).entryCategory()).isEqualTo(IncidentEntryCategory.EXTERNAL_CALL_FAILURE);
        assertThat(response.timeline().get(0).failureObserved()).isTrue();
    }

    @Test
    @DisplayName("9. Explicit timeout keyword in message or metadata categorizes as TIMEOUT_SIGNAL")
    void timeoutSignal_categorizedCorrectly() {
        String traceId = "trace-timeout-01";
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");

        List<TelemetryEvent> events = List.of(
                createEvent("evt-1", t0, "api-gateway", EventType.REQUEST, Severity.ERROR, traceId, "GET /catalog", "Gateway timeout after 5000ms", Map.of("timeoutMs", 5000))
        );

        when(telemetryRepository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId)).thenReturn(events);
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());

        IncidentTimelineResponse response = timelineService.getTimeline(traceId);

        assertThat(response.timeline().get(0).entryCategory()).isEqualTo(IncidentEntryCategory.TIMEOUT_SIGNAL);
        assertThat(response.timeline().get(0).failureObserved()).isTrue();
    }

    @Test
    @DisplayName("10. Persisted first-failure and temporal precedence evidence mapped to candidate entry")
    void persistedFirstFailureEvidence_mappedCorrectly() {
        String traceId = "trace-first-01";
        String analysisId = "analysis-first-01";
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");

        List<TelemetryEvent> events = List.of(
                createEvent("evt-1", t0, "payment-service", EventType.DATABASE, Severity.ERROR, traceId, "charge", "Lock timeout", Map.of())
        );

        FailureAnalysis analysis = new FailureAnalysis(
                analysisId, traceId, null, null,
                FailureAnalysisStatus.COMPLETED,
                FailureAnalysisConclusion.ROOT_CAUSE_CANDIDATE,
                new BigDecimal("0.9300"), 1, 1, 1, "payment-service", "evt-1", "Summary"
        );

        FailureAnalysisCandidate candidate = new FailureAnalysisCandidate(
                analysisId, 1, "payment-service", "evt-1", "DATABASE", "ERROR",
                FailureCandidateType.DATABASE_FAILURE, new BigDecimal("0.9300"), t0, "Database failure"
        );

        FailureAnalysisEvidence ev1 = new FailureAnalysisEvidence(
                analysisId, 1, 1, "evt-1",
                FailureEvidenceType.FIRST_FAILURE, "Earliest failure observed"
        );
        FailureAnalysisEvidence ev2 = new FailureAnalysisEvidence(
                analysisId, 1, 2, "evt-1",
                FailureEvidenceType.TEMPORAL_PRECEDENCE, "Event preceded subsequent failures"
        );

        when(telemetryRepository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId)).thenReturn(events);
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.of(analysis));
        when(candidateRepository.findAllByAnalysisIdOrderByRankAsc(analysisId)).thenReturn(List.of(candidate));
        when(evidenceRepository.findAllByAnalysisIdOrderByCandidateRankAscSequenceNumberAsc(analysisId)).thenReturn(List.of(ev1, ev2));
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());

        IncidentTimelineResponse response = timelineService.getTimeline(traceId);

        assertThat(response.analysisAvailable()).isTrue();
        assertThat(response.primaryServiceName()).isEqualTo("payment-service");
        assertThat(response.confidenceScore()).isEqualTo(new BigDecimal("0.9300"));

        var entry = response.timeline().get(0);
        assertThat(entry.relatedCandidateRank()).isEqualTo(1);
        assertThat(entry.evidenceReferences()).hasSize(2);
        assertThat(entry.evidenceReferences().get(0)).contains("FIRST_FAILURE");
        assertThat(entry.evidenceReferences().get(1)).contains("TEMPORAL_PRECEDENCE");
    }

    @Test
    @DisplayName("11. Persisted replay mismatch attaches REPLAY_DIVERGENCE evidence and category")
    void replayMismatch_attachesReplayDivergence() {
        String traceId = "trace-replay-diff-01";
        String scenarioId = "scen-diff-01";
        String verificationId = "verify-diff-01";
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");

        List<TelemetryEvent> events = List.of(
                createEvent("evt-1", t0, "order-service", EventType.REQUEST, Severity.INFO, traceId, "GET /order", "OK", Map.of())
        );

        ReplayScenario scenario = new ReplayScenario(scenarioId, traceId, 1, 0, 50L, ReplayScenarioStatus.COMPLETED);
        ReplayVerification verification = new ReplayVerification(
                verificationId, scenarioId, ReplayVerificationStatus.FAILED,
                1, 1, 0, 1, 50L, 50L, 0, 0, 1, 1, 0, 0, "Divergence"
        );
        ReplayVerificationDifference diff = new ReplayVerificationDifference(
                verificationId, 1, VerificationDifferenceType.SEVERITY_MISMATCH,
                "evt-1", "evt-1", "order-service", "order-service", "REQUEST", "REQUEST", "INFO", "ERROR", "Severity differed"
        );

        when(telemetryRepository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId)).thenReturn(events);
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.of(scenario));
        when(verificationRepository.findFirstByScenarioIdOrderByCreatedAtDesc(scenarioId)).thenReturn(Optional.of(verification));
        when(differenceRepository.findAllByVerificationIdOrderBySequenceNumberAsc(verificationId)).thenReturn(List.of(diff));

        IncidentTimelineResponse response = timelineService.getTimeline(traceId);

        assertThat(response.verificationAvailable()).isTrue();
        var entry = response.timeline().get(0);
        assertThat(entry.entryCategory()).isEqualTo(IncidentEntryCategory.REPLAY_DIVERGENCE);
        assertThat(entry.evidenceReferences())
                .anyMatch(r -> r.contains("REPLAY_DIVERGENCE: SEVERITY_MISMATCH"));
    }

    @Test
    @DisplayName("12. Temporal order alone does not claim failure propagation without analysis evidence")
    void temporalOrderAlone_doesNotClaimPropagation() {
        String traceId = "trace-prop-01";
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");
        Instant t1 = Instant.parse("2026-10-18T10:00:00.100Z");

        // Two failures without analysis evidence proving propagation
        List<TelemetryEvent> events = List.of(
                createEvent("evt-1", t0, "payment-service", EventType.DATABASE, Severity.ERROR, traceId, "charge", "Lock", Map.of()),
                createEvent("evt-2", t1, "notification-service", EventType.REQUEST, Severity.ERROR, traceId, "send", "Err", Map.of())
        );

        when(telemetryRepository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId)).thenReturn(events);
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());

        IncidentTimelineResponse response = timelineService.getTimeline(traceId);

        // Neither should claim failure propagation without evidence
        assertThat(response.timeline().get(0).failurePropagation()).isFalse();
        assertThat(response.timeline().get(1).failurePropagation()).isFalse();
    }

    @Test
    @DisplayName("13. Analysis unavailable reports analysisAvailable=false without failure")
    void analysisUnavailable_reportsFalse() {
        String traceId = "trace-no-analysis-01";
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");

        List<TelemetryEvent> events = List.of(
                createEvent("evt-1", t0, "order-service", EventType.DATABASE, Severity.ERROR, traceId, "query", "Failed", Map.of())
        );

        when(telemetryRepository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId)).thenReturn(events);
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());

        IncidentTimelineResponse response = timelineService.getTimeline(traceId);

        assertThat(response.analysisAvailable()).isFalse();
        assertThat(response.primaryServiceName()).isNull();
        assertThat(response.confidenceScore()).isNull();
    }

    @Test
    @DisplayName("14. Verification unavailable reports verificationAvailable=false")
    void verificationUnavailable_reportsFalse() {
        String traceId = "trace-no-verif-01";
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");

        List<TelemetryEvent> events = List.of(
                createEvent("evt-1", t0, "order-service", EventType.REQUEST, Severity.INFO, traceId, "GET /", "OK", Map.of())
        );

        when(telemetryRepository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId)).thenReturn(events);
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());

        IncidentTimelineResponse response = timelineService.getTimeline(traceId);

        assertThat(response.verificationAvailable()).isFalse();
    }

    @Test
    @DisplayName("15. Repeated retrieval generates completely deterministic output")
    void repeatedRetrieval_isDeterministic() {
        String traceId = "trace-det-01";
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");

        List<TelemetryEvent> events = List.of(
                createEvent("evt-1", t0, "order-service", EventType.REQUEST, Severity.INFO, traceId, "GET /", "OK", Map.of())
        );

        when(telemetryRepository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId)).thenReturn(events);
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());

        IncidentTimelineResponse res1 = timelineService.getTimeline(traceId);
        IncidentTimelineResponse res2 = timelineService.getTimeline(traceId);

        assertThat(res1).isEqualTo(res2);
    }

    @Test
    @DisplayName("16. Read-only guarantee: no repository save/delete methods are invoked")
    void readOnlyGuarantee_noMutationsOccur() {
        String traceId = "trace-readonly-01";
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");

        List<TelemetryEvent> events = List.of(
                createEvent("evt-1", t0, "order-service", EventType.DATABASE, Severity.ERROR, traceId, "query", "Failed", Map.of())
        );

        when(telemetryRepository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId)).thenReturn(events);
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());

        timelineService.getTimeline(traceId);

        verify(telemetryRepository, never()).save(any());
        verify(telemetryRepository, never()).saveAndFlush(any());
        verify(telemetryRepository, never()).delete(any(TelemetryEvent.class));
        verify(analysisRepository, never()).save(any());
        verify(analysisRepository, never()).saveAndFlush(any());
        verify(scenarioRepository, never()).save(any());
        verify(scenarioRepository, never()).saveAndFlush(any());
        verify(verificationRepository, never()).save(any());
        verify(verificationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("17. Identical timestamps with deterministic event ID ordering")
    void identicalTimestamps_deterministicEventIdOrdering() {
        String traceId = "trace-identical-ts-01";
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");

        List<TelemetryEvent> events = List.of(
                createEvent("evt-03", t0, "svc-c", EventType.REQUEST, Severity.INFO, traceId, "opC", "Msg", Map.of()),
                createEvent("evt-01", t0, "svc-a", EventType.REQUEST, Severity.INFO, traceId, "opA", "Msg", Map.of()),
                createEvent("evt-02", t0, "svc-b", EventType.REQUEST, Severity.INFO, traceId, "opB", "Msg", Map.of())
        );

        when(telemetryRepository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId))
                .thenReturn(List.of(events.get(1), events.get(2), events.get(0)));
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());

        IncidentTimelineResponse response = timelineService.getTimeline(traceId);

        assertThat(response.timeline()).hasSize(3);
        assertThat(response.timeline().get(0).eventId()).isEqualTo("evt-01");
        assertThat(response.timeline().get(1).eventId()).isEqualTo("evt-02");
        assertThat(response.timeline().get(2).eventId()).isEqualTo("evt-03");
        assertThat(response.timeline().get(0).offsetMs()).isZero();
        assertThat(response.timeline().get(1).offsetMs()).isZero();
        assertThat(response.timeline().get(2).offsetMs()).isZero();
    }

    @Test
    @DisplayName("18. Independent failures across services without propagation signals are not classified downstream")
    void independentFailuresAcrossServices_doesNotClassifyDownstream() {
        String traceId = "trace-indep-timeline-01";
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");
        Instant t1 = Instant.parse("2026-10-18T10:00:00.080Z");

        List<TelemetryEvent> events = List.of(
                createEvent("evt-1", t0, "billing-service", EventType.DATABASE, Severity.ERROR, traceId, "charge", "Database deadlock", Map.of()),
                createEvent("evt-2", t1, "email-service", EventType.REQUEST, Severity.ERROR, traceId, "sendMail", "Failed to resolve SMTP host", Map.of())
        );

        when(telemetryRepository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId)).thenReturn(events);
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());

        IncidentTimelineResponse response = timelineService.getTimeline(traceId);

        assertThat(response.timeline().get(0).entryCategory()).isEqualTo(IncidentEntryCategory.DATABASE_FAILURE);
        assertThat(response.timeline().get(0).failurePropagation()).isFalse();

        // evt-2 is an independent failure in email-service without propagation evidence -> ERROR_EVENT, failurePropagation = false
        assertThat(response.timeline().get(1).entryCategory()).isEqualTo(IncidentEntryCategory.ERROR_EVENT);
        assertThat(response.timeline().get(1).failurePropagation()).isFalse();
    }

    @Test
    @DisplayName("19. Downstream failure with explicit propagation evidence is categorized as DOWNSTREAM_ERROR")
    void downstreamFailureWithPropagationSignal_categorizesDownstream() {
        String traceId = "trace-prop-timeline-01";
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");
        Instant t1 = Instant.parse("2026-10-18T10:00:00.120Z");

        List<TelemetryEvent> events = List.of(
                createEvent("evt-1", t0, "inventory-service", EventType.DATABASE, Severity.FATAL, traceId, "reserve", "Deadlock", Map.of()),
                createEvent("evt-2", t1, "order-service", EventType.RESPONSE, Severity.ERROR, traceId, "createOrder", "Order failed due to inventory-service downstream error", Map.of())
        );

        when(telemetryRepository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId)).thenReturn(events);
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());

        IncidentTimelineResponse response = timelineService.getTimeline(traceId);

        assertThat(response.timeline().get(0).entryCategory()).isEqualTo(IncidentEntryCategory.DATABASE_FAILURE);
        assertThat(response.timeline().get(0).failurePropagation()).isFalse();

        assertThat(response.timeline().get(1).entryCategory()).isEqualTo(IncidentEntryCategory.DOWNSTREAM_ERROR);
        assertThat(response.timeline().get(1).failurePropagation()).isTrue();
    }

    @Test
    @DisplayName("20. Causal timestamp anomaly detection flags inverted request/response ordering")
    void detectCausalTimestampAnomalies_detectsInvertedResponseTimestamp() {
        Instant t0 = Instant.parse("2026-10-18T10:00:00.100Z");
        Instant tBefore = Instant.parse("2026-10-18T10:00:00.020Z"); // earlier timestamp due to clock skew!

        TelemetryEvent req = new TelemetryEvent(
                "evt-req", t0, "order-service", "node-1", EventType.REQUEST, Severity.INFO,
                "trace-skew-01", "req-skew-01", "POST /orders", "Request received", Map.of()
        );
        TelemetryEvent res = new TelemetryEvent(
                "evt-res", tBefore, "order-service", "node-1", EventType.RESPONSE, Severity.ERROR,
                "trace-skew-01", "req-skew-01", "POST /orders", "Response returned", Map.of()
        );

        // Anomaly present: response timestamp is before request timestamp
        boolean anomaly = timelineService.detectCausalTimestampAnomalies(List.of(req, res));
        assertThat(anomaly).isTrue();

        // Normal monotonic ordering: response timestamp after request timestamp
        TelemetryEvent resNormal = new TelemetryEvent(
                "evt-res-norm", Instant.parse("2026-10-18T10:00:00.150Z"), "order-service", "node-1", EventType.RESPONSE, Severity.INFO,
                "trace-skew-01", "req-skew-01", "POST /orders", "Response returned", Map.of()
        );
        boolean normal = timelineService.detectCausalTimestampAnomalies(List.of(req, resNormal));
        assertThat(normal).isFalse();
    }

    @Test
    @DisplayName("21. Retries and multiple calls sharing requestId do not trigger false positive timestamp anomaly")
    void detectCausalTimestampAnomalies_doesNotFalsePositiveOnRetries() {
        // Attempt 1: req at 100ms, res at 120ms
        TelemetryEvent req1 = new TelemetryEvent(
                "evt-req-1", Instant.parse("2026-10-18T10:00:00.100Z"), "order-service", "node-1",
                EventType.EXTERNAL_CALL, Severity.INFO, "trace-retry-01", "req-shared-01", "GET /inventory",
                "Calling inventory attempt 1", Map.of()
        );
        TelemetryEvent res1 = new TelemetryEvent(
                "evt-res-1", Instant.parse("2026-10-18T10:00:00.120Z"), "inventory-service", "node-2",
                EventType.RESPONSE, Severity.WARN, "trace-retry-01", "req-shared-01", "GET /inventory",
                "Temporary 503 response", Map.of()
        );

        // Attempt 2 (retry): req at 150ms, res at 170ms
        // Under naive comparison (res1.timestamp < req2.timestamp = 120ms < 150ms), this would false-positive!
        TelemetryEvent req2 = new TelemetryEvent(
                "evt-req-2", Instant.parse("2026-10-18T10:00:00.150Z"), "order-service", "node-1",
                EventType.EXTERNAL_CALL, Severity.INFO, "trace-retry-01", "req-shared-01", "GET /inventory",
                "Calling inventory attempt 2", Map.of()
        );
        TelemetryEvent res2 = new TelemetryEvent(
                "evt-res-2", Instant.parse("2026-10-18T10:00:00.170Z"), "inventory-service", "node-2",
                EventType.RESPONSE, Severity.INFO, "trace-retry-01", "req-shared-01", "GET /inventory",
                "Success response", Map.of()
        );

        boolean anomaly = timelineService.detectCausalTimestampAnomalies(List.of(req1, res1, req2, res2));
        assertThat(anomaly).isFalse();
    }

    @Test
    @DisplayName("22. Equal timestamps between request and response do not trigger causal anomaly")
    void detectCausalTimestampAnomalies_equalTimestampsDoNotTriggerAnomaly() {
        Instant sameInstant = Instant.parse("2026-10-18T10:00:00.100Z");
        TelemetryEvent req = new TelemetryEvent(
                "evt-req", sameInstant, "order-service", "node-1", EventType.REQUEST, Severity.INFO,
                "trace-eq-01", "req-eq-01", "POST /orders", "Request received", Map.of()
        );
        TelemetryEvent res = new TelemetryEvent(
                "evt-res", sameInstant, "order-service", "node-1", EventType.RESPONSE, Severity.INFO,
                "trace-eq-01", "req-eq-01", "POST /orders", "Response returned same millisecond", Map.of()
        );

        boolean anomaly = timelineService.detectCausalTimestampAnomalies(List.of(req, res));
        assertThat(anomaly).isFalse();
    }

    @Test
    @DisplayName("23. Retried call with clock skew on second attempt is accurately detected")
    void detectCausalTimestampAnomalies_skewOnSecondAttemptDetected() {
        TelemetryEvent req1 = new TelemetryEvent(
                "evt-req-1", Instant.parse("2026-10-18T10:00:00.100Z"), "order-service", "node-1",
                EventType.EXTERNAL_CALL, Severity.INFO, "trace-retry-02", "req-shared-02", "GET /inventory",
                "Calling inventory attempt 1", Map.of()
        );
        TelemetryEvent res1 = new TelemetryEvent(
                "evt-res-1", Instant.parse("2026-10-18T10:00:00.120Z"), "inventory-service", "node-2",
                EventType.RESPONSE, Severity.WARN, "trace-retry-02", "req-shared-02", "GET /inventory",
                "Temporary 503 response", Map.of()
        );

        // Attempt 2: req at 200ms, but clock skewed node returns response stamped at 180ms (< 200ms)
        TelemetryEvent req2 = new TelemetryEvent(
                "evt-req-2", Instant.parse("2026-10-18T10:00:00.200Z"), "order-service", "node-1",
                EventType.EXTERNAL_CALL, Severity.INFO, "trace-retry-02", "req-shared-02", "GET /inventory",
                "Calling inventory attempt 2", Map.of()
        );
        TelemetryEvent res2 = new TelemetryEvent(
                "evt-res-2", Instant.parse("2026-10-18T10:00:00.180Z"), "inventory-service", "node-skew",
                EventType.RESPONSE, Severity.INFO, "trace-retry-02", "req-shared-02", "GET /inventory",
                "Success response with clock skew", Map.of()
        );

        boolean anomaly = timelineService.detectCausalTimestampAnomalies(List.of(req1, res1, req2, res2));
        assertThat(anomaly).isTrue();
    }
}
