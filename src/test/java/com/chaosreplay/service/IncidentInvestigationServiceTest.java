package com.chaosreplay.service;

import com.chaosreplay.api.dto.IncidentInvestigationResponse;
import com.chaosreplay.api.dto.IncidentTimelineResponse;
import com.chaosreplay.domain.FailureAnalysis;
import com.chaosreplay.domain.FailureAnalysisCandidate;
import com.chaosreplay.domain.FailureAnalysisConclusion;
import com.chaosreplay.domain.FailureAnalysisEvidence;
import com.chaosreplay.domain.FailureAnalysisStatus;
import com.chaosreplay.domain.FailureCandidateType;
import com.chaosreplay.domain.FailureEvidenceType;
import com.chaosreplay.domain.ReplayScenario;
import com.chaosreplay.domain.ReplayScenarioStatus;
import com.chaosreplay.domain.ReplayVerification;
import com.chaosreplay.domain.ReplayVerificationDifference;
import com.chaosreplay.domain.ReplayVerificationStatus;
import com.chaosreplay.domain.VerificationDifferenceType;
import com.chaosreplay.exception.ResourceNotFoundException;
import com.chaosreplay.repository.FailureAnalysisCandidateRepository;
import com.chaosreplay.repository.FailureAnalysisEvidenceRepository;
import com.chaosreplay.repository.FailureAnalysisRepository;
import com.chaosreplay.repository.ReplayScenarioRepository;
import com.chaosreplay.repository.ReplayVerificationDifferenceRepository;
import com.chaosreplay.repository.ReplayVerificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentInvestigationServiceTest {

    @Mock
    private IncidentTimelineService timelineService;
    @Mock
    private ReplayScenarioRepository scenarioRepository;
    @Mock
    private ReplayVerificationRepository verificationRepository;
    @Mock
    private ReplayVerificationDifferenceRepository differenceRepository;
    @Mock
    private FailureAnalysisRepository analysisRepository;
    @Mock
    private FailureAnalysisCandidateRepository candidateRepository;
    @Mock
    private FailureAnalysisEvidenceRepository evidenceRepository;

    private IncidentInvestigationService investigationService;

    @BeforeEach
    void setUp() {
        investigationService = new IncidentInvestigationService(
                timelineService,
                scenarioRepository,
                verificationRepository,
                differenceRepository,
                analysisRepository,
                candidateRepository,
                evidenceRepository
        );
    }

    private IncidentTimelineResponse createDummyTimeline(String traceId, boolean failureObserved) {
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");
        Instant t1 = Instant.parse("2026-10-18T10:00:00.100Z");
        return new IncidentTimelineResponse(
                traceId,
                2,
                failureObserved ? 1 : 0,
                failureObserved,
                t0,
                t1,
                100L,
                false,
                false,
                failureObserved ? null : FailureAnalysisStatus.INSUFFICIENT_EVIDENCE,
                failureObserved ? null : FailureAnalysisConclusion.NO_FAILURE_OBSERVED,
                null,
                null,
                failureObserved ? null : BigDecimal.ZERO.setScale(4),
                List.of(),
                t1
        );
    }

    @Test
    @DisplayName("1. Complete investigation with telemetry, scenario, execution, verification, and analysis")
    void completeInvestigation_aggregatesAllStages() {
        String traceId = "trace-full-01";
        String scenarioId = "scen-full-01";
        String verificationId = "verify-full-01";
        String analysisId = "analysis-full-01";
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");

        IncidentTimelineResponse timeline = createDummyTimeline(traceId, true);
        ReplayScenario scenario = new ReplayScenario(scenarioId, traceId, 2, 1, 100L, ReplayScenarioStatus.COMPLETED);
        ReplayVerification verification = new ReplayVerification(
                verificationId, scenarioId, ReplayVerificationStatus.PASSED,
                2, 2, 1, 1, 100L, 100L, 2, 0, 0, 0, 0, 0, "Replay verified successfully"
        );
        FailureAnalysis analysis = new FailureAnalysis(
                analysisId, traceId, scenarioId, verificationId,
                FailureAnalysisStatus.COMPLETED, FailureAnalysisConclusion.ROOT_CAUSE_CANDIDATE,
                new BigDecimal("0.9600"), 2, 1, 1, "payment-service", "evt-1", "Summary"
        );
        FailureAnalysisCandidate candidate = new FailureAnalysisCandidate(
                analysisId, 1, "payment-service", "evt-1", "DATABASE", "ERROR",
                FailureCandidateType.DATABASE_FAILURE, new BigDecimal("0.9600"), t0, "Deadlock"
        );
        FailureAnalysisEvidence evidence = new FailureAnalysisEvidence(
                analysisId, 1, 1, "evt-1",
                FailureEvidenceType.DATABASE_ERROR, "Database lock acquired failed"
        );

        when(timelineService.getTimeline(traceId)).thenReturn(timeline);
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.of(scenario));
        when(verificationRepository.findFirstByScenarioIdOrderByCreatedAtDesc(scenarioId)).thenReturn(Optional.of(verification));
        when(differenceRepository.findAllByVerificationIdOrderBySequenceNumberAsc(verificationId)).thenReturn(List.of());
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.of(analysis));
        when(candidateRepository.findAllByAnalysisIdOrderByRankAsc(analysisId)).thenReturn(List.of(candidate));
        when(evidenceRepository.findAllByAnalysisIdOrderByCandidateRankAscSequenceNumberAsc(analysisId)).thenReturn(List.of(evidence));

        IncidentInvestigationResponse response = investigationService.getInvestigation(traceId);

        assertThat(response.correlationAvailable()).isTrue();
        assertThat(response.scenarioAvailable()).isTrue();
        assertThat(response.scenarioId()).isEqualTo(scenarioId);
        assertThat(response.executionAvailable()).isTrue();
        assertThat(response.executionStatus()).isEqualTo(ReplayScenarioStatus.COMPLETED);
        assertThat(response.verificationAvailable()).isTrue();
        assertThat(response.verificationId()).isEqualTo(verificationId);
        assertThat(response.verificationStatus()).isEqualTo(ReplayVerificationStatus.PASSED);
        assertThat(response.analysisAvailable()).isTrue();
        assertThat(response.analysisId()).isEqualTo(analysisId);
        assertThat(response.conclusion()).isEqualTo(FailureAnalysisConclusion.ROOT_CAUSE_CANDIDATE);
        assertThat(response.confidenceScore()).isEqualTo(new BigDecimal("0.9600"));
        assertThat(response.candidates()).hasSize(1);
        assertThat(response.evidenceSummary()).isNotEmpty();
    }

    @Test
    @DisplayName("2. Investigation when analysis is absent reports analysisAvailable=false and limitations")
    void analysisAbsent_reportsLimitation() {
        String traceId = "trace-no-analysis-01";
        IncidentTimelineResponse timeline = createDummyTimeline(traceId, true);

        when(timelineService.getTimeline(traceId)).thenReturn(timeline);
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());

        IncidentInvestigationResponse response = investigationService.getInvestigation(traceId);

        assertThat(response.analysisAvailable()).isFalse();
        assertThat(response.analysisId()).isNull();
        assertThat(response.candidates()).isEmpty();
        assertThat(response.limitations())
                .anyMatch(l -> l.contains("Failure analysis has not been performed"));
    }

    @Test
    @DisplayName("3. Investigation when verification is absent reports verificationAvailable=false")
    void verificationAbsent_reportsFalse() {
        String traceId = "trace-no-verif-01";
        String scenarioId = "scen-01";
        IncidentTimelineResponse timeline = createDummyTimeline(traceId, true);
        ReplayScenario scenario = new ReplayScenario(scenarioId, traceId, 2, 1, 100L, ReplayScenarioStatus.CREATED);

        when(timelineService.getTimeline(traceId)).thenReturn(timeline);
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.of(scenario));
        when(verificationRepository.findFirstByScenarioIdOrderByCreatedAtDesc(scenarioId)).thenReturn(Optional.empty());
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());

        IncidentInvestigationResponse response = investigationService.getInvestigation(traceId);

        assertThat(response.verificationAvailable()).isFalse();
        assertThat(response.verificationId()).isNull();
        assertThat(response.executionAvailable()).isFalse(); // Status is CREATED
        assertThat(response.limitations())
                .anyMatch(l -> l.contains("Replay verification has not been performed"));
    }

    @Test
    @DisplayName("4. Investigation when scenario is absent reports scenarioAvailable=false")
    void scenarioAbsent_reportsFalse() {
        String traceId = "trace-no-scen-01";
        IncidentTimelineResponse timeline = createDummyTimeline(traceId, true);

        when(timelineService.getTimeline(traceId)).thenReturn(timeline);
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());

        IncidentInvestigationResponse response = investigationService.getInvestigation(traceId);

        assertThat(response.scenarioAvailable()).isFalse();
        assertThat(response.verificationAvailable()).isFalse();
        assertThat(response.limitations())
                .anyMatch(l -> l.contains("Replay scenario has not been generated"));
    }

    @Test
    @DisplayName("5. Investigation for trace with no failures returns NO_FAILURE_OBSERVED and limitation")
    void cleanTrace_reportsNoFailureObserved() {
        String traceId = "trace-clean-01";
        IncidentTimelineResponse timeline = createDummyTimeline(traceId, false);

        when(timelineService.getTimeline(traceId)).thenReturn(timeline);
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());

        IncidentInvestigationResponse response = investigationService.getInvestigation(traceId);

        assertThat(response.analysisStatus()).isEqualTo(FailureAnalysisStatus.INSUFFICIENT_EVIDENCE);
        assertThat(response.conclusion()).isEqualTo(FailureAnalysisConclusion.NO_FAILURE_OBSERVED);
        assertThat(response.confidenceScore()).isEqualTo(new BigDecimal("0.0000"));
        assertThat(response.limitations())
                .anyMatch(l -> l.contains("No failure events were observed"));
    }

    @Test
    @DisplayName("6. Investigation for unknown trace throws ResourceNotFoundException")
    void unknownTrace_throwsResourceNotFoundException() {
        when(timelineService.getTimeline("unknown-trace"))
                .thenThrow(new ResourceNotFoundException("Trace not found: unknown-trace"));

        assertThatThrownBy(() -> investigationService.getInvestigation("unknown-trace"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Trace not found: unknown-trace");
    }

    @Test
    @DisplayName("7. Correct propagation of verification FAILED and PARTIAL statuses")
    void verificationStatusPropagation_reportsFailedAndPartialCorrectly() {
        String traceId = "trace-verif-failed-01";
        String scenarioId = "scen-02";
        String verificationId = "verif-02";

        IncidentTimelineResponse timeline = createDummyTimeline(traceId, true);
        ReplayScenario scenario = new ReplayScenario(scenarioId, traceId, 2, 1, 100L, ReplayScenarioStatus.COMPLETED);
        ReplayVerification verificationFailed = new ReplayVerification(
                verificationId, scenarioId, ReplayVerificationStatus.FAILED,
                2, 2, 1, 0, 100L, 100L, 1, 1, 0, 0, 0, 0, "Missing failure"
        );
        ReplayVerificationDifference diff = new ReplayVerificationDifference(
                verificationId, 1, VerificationDifferenceType.MISSING_EVENT,
                "evt-2", null, "order-service", null, "ERROR", null, "ERROR", null, "Event missing"
        );

        when(timelineService.getTimeline(traceId)).thenReturn(timeline);
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.of(scenario));
        when(verificationRepository.findFirstByScenarioIdOrderByCreatedAtDesc(scenarioId)).thenReturn(Optional.of(verificationFailed));
        when(differenceRepository.findAllByVerificationIdOrderBySequenceNumberAsc(verificationId)).thenReturn(List.of(diff));
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());

        IncidentInvestigationResponse response = investigationService.getInvestigation(traceId);

        assertThat(response.verificationStatus()).isEqualTo(ReplayVerificationStatus.FAILED);
        assertThat(response.limitations())
                .anyMatch(l -> l.contains("Replay verification FAILED"));
        assertThat(response.evidenceSummary())
                .anyMatch(e -> e.contains("Divergence at seq #1 [MISSING_EVENT]"));
    }

    @Test
    @DisplayName("8. Root-cause candidates and supporting evidence correctly mapped into response")
    void candidatesAndEvidence_mappedCorrectly() {
        String traceId = "trace-cand-01";
        String analysisId = "analysis-01";
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");

        IncidentTimelineResponse timeline = createDummyTimeline(traceId, true);
        FailureAnalysis analysis = new FailureAnalysis(
                analysisId, traceId, null, null,
                FailureAnalysisStatus.COMPLETED, FailureAnalysisConclusion.ROOT_CAUSE_CANDIDATE,
                new BigDecimal("0.9000"), 1, 1, 1, "order-service", "evt-1", "Summary"
        );
        FailureAnalysisCandidate candidate = new FailureAnalysisCandidate(
                analysisId, 1, "order-service", "evt-1", "DATABASE", "ERROR",
                FailureCandidateType.DATABASE_FAILURE, new BigDecimal("0.9000"), t0, "Desc"
        );
        FailureAnalysisEvidence ev = new FailureAnalysisEvidence(
                analysisId, 1, 1, "evt-1", FailureEvidenceType.DATABASE_ERROR, "DB crashed"
        );

        when(timelineService.getTimeline(traceId)).thenReturn(timeline);
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.of(analysis));
        when(candidateRepository.findAllByAnalysisIdOrderByRankAsc(analysisId)).thenReturn(List.of(candidate));
        when(evidenceRepository.findAllByAnalysisIdOrderByCandidateRankAscSequenceNumberAsc(analysisId)).thenReturn(List.of(ev));

        IncidentInvestigationResponse response = investigationService.getInvestigation(traceId);

        assertThat(response.candidates()).hasSize(1);
        assertThat(response.candidates().get(0).serviceName()).isEqualTo("order-service");
        assertThat(response.candidates().get(0).evidence()).hasSize(1);
        assertThat(response.candidates().get(0).evidence().get(0).evidenceValue()).isEqualTo("DB crashed");
    }

    @Test
    @DisplayName("9. Missing evidence is reported as a limitation rather than a successful result")
    void missingEvidence_reportedAsLimitation() {
        String traceId = "trace-inconcl-01";
        String analysisId = "analysis-inconcl-01";

        IncidentTimelineResponse timeline = createDummyTimeline(traceId, true);
        FailureAnalysis analysis = new FailureAnalysis(
                analysisId, traceId, null, null,
                FailureAnalysisStatus.INSUFFICIENT_EVIDENCE, FailureAnalysisConclusion.INSUFFICIENT_EVIDENCE,
                BigDecimal.ZERO.setScale(4), 1, 1, 0, null, null, "Summary"
        );

        when(timelineService.getTimeline(traceId)).thenReturn(timeline);
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.of(analysis));
        when(candidateRepository.findAllByAnalysisIdOrderByRankAsc(analysisId)).thenReturn(List.of());
        when(evidenceRepository.findAllByAnalysisIdOrderByCandidateRankAscSequenceNumberAsc(analysisId)).thenReturn(List.of());

        IncidentInvestigationResponse response = investigationService.getInvestigation(traceId);

        assertThat(response.analysisStatus()).isEqualTo(FailureAnalysisStatus.INSUFFICIENT_EVIDENCE);
        assertThat(response.limitations())
                .anyMatch(l -> l.contains("Insufficient telemetry evidence"));
    }

    @Test
    @DisplayName("10. Read-only guarantee: reading investigation triggers zero workflow creations or mutations")
    void readOnlyGuarantee_noSideEffects() {
        String traceId = "trace-ro-01";
        IncidentTimelineResponse timeline = createDummyTimeline(traceId, true);

        when(timelineService.getTimeline(traceId)).thenReturn(timeline);
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());

        investigationService.getInvestigation(traceId);

        verify(scenarioRepository, never()).save(any());
        verify(scenarioRepository, never()).saveAndFlush(any());
        verify(verificationRepository, never()).save(any());
        verify(verificationRepository, never()).saveAndFlush(any());
        verify(analysisRepository, never()).save(any());
        verify(analysisRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("11. Clock-skew limitation is explicitly reported in investigation limitations")
    void clockSkewLimitation_alwaysReported() {
        String traceId = "trace-skew-lim-01";
        IncidentTimelineResponse timeline = createDummyTimeline(traceId, true);

        when(timelineService.getTimeline(traceId)).thenReturn(timeline);
        when(timelineService.hasCausalTimestampAnomaly(traceId)).thenReturn(false);
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());

        IncidentInvestigationResponse response = investigationService.getInvestigation(traceId);

        assertThat(response.limitations())
                .anyMatch(l -> l.contains("Telemetry timestamps reflect producer host clocks")
                        && l.contains("do not correct for inter-service clock skew"));
    }

    @Test
    @DisplayName("12. Causal timestamp anomaly detected is explicitly reported in investigation limitations")
    void causalTimestampAnomaly_reportedInLimitations() {
        String traceId = "trace-skew-anomaly-01";
        IncidentTimelineResponse timeline = createDummyTimeline(traceId, true);

        when(timelineService.getTimeline(traceId)).thenReturn(timeline);
        when(timelineService.hasCausalTimestampAnomaly(traceId)).thenReturn(true);
        when(scenarioRepository.findFirstBySourceTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());
        when(analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)).thenReturn(Optional.empty());

        IncidentInvestigationResponse response = investigationService.getInvestigation(traceId);

        assertThat(response.limitations())
                .anyMatch(l -> l.contains("Potential cross-service clock anomaly detected"));
    }
}
