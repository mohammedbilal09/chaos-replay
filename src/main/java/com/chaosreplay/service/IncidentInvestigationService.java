package com.chaosreplay.service;

import com.chaosreplay.api.dto.FailureAnalysisCandidateResponse;
import com.chaosreplay.api.dto.FailureAnalysisEvidenceResponse;
import com.chaosreplay.api.dto.IncidentInvestigationResponse;
import com.chaosreplay.api.dto.IncidentTimelineResponse;
import com.chaosreplay.domain.FailureAnalysis;
import com.chaosreplay.domain.FailureAnalysisCandidate;
import com.chaosreplay.domain.FailureAnalysisConclusion;
import com.chaosreplay.domain.FailureAnalysisEvidence;
import com.chaosreplay.domain.FailureAnalysisStatus;
import com.chaosreplay.domain.ReplayScenario;
import com.chaosreplay.domain.ReplayScenarioStatus;
import com.chaosreplay.domain.ReplayVerification;
import com.chaosreplay.domain.ReplayVerificationDifference;
import com.chaosreplay.domain.ReplayVerificationStatus;
import com.chaosreplay.exception.ResourceNotFoundException;
import com.chaosreplay.repository.FailureAnalysisCandidateRepository;
import com.chaosreplay.repository.FailureAnalysisEvidenceRepository;
import com.chaosreplay.repository.FailureAnalysisRepository;
import com.chaosreplay.repository.ReplayScenarioRepository;
import com.chaosreplay.repository.ReplayVerificationDifferenceRepository;
import com.chaosreplay.repository.ReplayVerificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.chaosreplay.metrics.ChaosReplayMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Read-only service aggregating telemetry timeline, replay scenarios, simulation execution,
 * verification differences, and deterministic failure analysis into a consolidated investigation view.
 */
@Service
public class IncidentInvestigationService {

    private static final Logger log = LoggerFactory.getLogger(IncidentInvestigationService.class);

    private final IncidentTimelineService timelineService;
    private final ReplayScenarioRepository scenarioRepository;
    private final ReplayVerificationRepository verificationRepository;
    private final ReplayVerificationDifferenceRepository differenceRepository;
    private final FailureAnalysisRepository analysisRepository;
    private final FailureAnalysisCandidateRepository candidateRepository;
    private final FailureAnalysisEvidenceRepository evidenceRepository;
    private final ChaosReplayMetrics metrics;

    public IncidentInvestigationService(
            IncidentTimelineService timelineService,
            ReplayScenarioRepository scenarioRepository,
            ReplayVerificationRepository verificationRepository,
            ReplayVerificationDifferenceRepository differenceRepository,
            FailureAnalysisRepository analysisRepository,
            FailureAnalysisCandidateRepository candidateRepository,
            FailureAnalysisEvidenceRepository evidenceRepository
    ) {
        this(timelineService, scenarioRepository, verificationRepository, differenceRepository,
                analysisRepository, candidateRepository, evidenceRepository,
                new ChaosReplayMetrics(new SimpleMeterRegistry()));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public IncidentInvestigationService(
            IncidentTimelineService timelineService,
            ReplayScenarioRepository scenarioRepository,
            ReplayVerificationRepository verificationRepository,
            ReplayVerificationDifferenceRepository differenceRepository,
            FailureAnalysisRepository analysisRepository,
            FailureAnalysisCandidateRepository candidateRepository,
            FailureAnalysisEvidenceRepository evidenceRepository,
            ChaosReplayMetrics metrics
    ) {
        this.timelineService = timelineService;
        this.scenarioRepository = scenarioRepository;
        this.verificationRepository = verificationRepository;
        this.differenceRepository = differenceRepository;
        this.analysisRepository = analysisRepository;
        this.candidateRepository = candidateRepository;
        this.evidenceRepository = evidenceRepository;
        this.metrics = metrics != null ? metrics : new ChaosReplayMetrics(new SimpleMeterRegistry());
    }

    /**
     * Consolidates an end-to-end incident investigation view for a distributed trace.
     *
     * @param traceId distributed trace identifier
     * @return consolidated incident investigation response
     * @throws ResourceNotFoundException if trace has no telemetry events
     */
    @Transactional(readOnly = true)
    public IncidentInvestigationResponse getInvestigation(String traceId) {
        long startNanos = System.nanoTime();
        if (traceId == null || traceId.isBlank()) {
            metrics.recordInvestigationDuration("not_found", System.nanoTime() - startNanos);
            throw new ResourceNotFoundException("Trace not found: " + traceId);
        }

        String cleanTraceId = traceId.trim();

        // 1. Retrieve timeline (validates trace existence and throws 404 if missing)
        IncidentTimelineResponse timeline = timelineService.getTimeline(cleanTraceId);

        // 2. Retrieve latest scenario
        Optional<ReplayScenario> optScenario = scenarioRepository
                .findFirstBySourceTraceIdOrderByCreatedAtDesc(cleanTraceId);
        ReplayScenario scenario = optScenario.orElse(null);
        boolean scenarioAvailable = scenario != null;
        String scenarioId = scenario != null ? scenario.getScenarioId() : null;
        boolean executionAvailable = scenario != null && scenario.getStatus() != ReplayScenarioStatus.CREATED;
        ReplayScenarioStatus executionStatus = scenario != null ? scenario.getStatus() : null;

        // 3. Retrieve latest verification
        Optional<ReplayVerification> optVerification = scenario != null
                ? verificationRepository.findFirstByScenarioIdOrderByCreatedAtDesc(scenario.getScenarioId())
                : Optional.empty();
        ReplayVerification verification = optVerification.orElse(null);
        boolean verificationAvailable = verification != null;
        String verificationId = verification != null ? verification.getVerificationId() : null;
        ReplayVerificationStatus verificationStatus = verification != null ? verification.getStatus() : null;
        String verificationSummary = verification != null ? verification.getResultMessage() : null;

        List<ReplayVerificationDifference> differences = verification != null
                ? differenceRepository.findAllByVerificationIdOrderBySequenceNumberAsc(verification.getVerificationId())
                : List.of();

        // 4. Retrieve latest failure analysis
        Optional<FailureAnalysis> optAnalysis = analysisRepository
                .findFirstByTraceIdOrderByCreatedAtDesc(cleanTraceId);
        FailureAnalysis analysis = optAnalysis.orElse(null);
        boolean analysisAvailable = analysis != null;

        String analysisId = analysis != null ? analysis.getAnalysisId() : null;
        FailureAnalysisStatus analysisStatus = analysis != null
                ? analysis.getStatus()
                : (timeline.failureObserved() ? null : FailureAnalysisStatus.INSUFFICIENT_EVIDENCE);
        FailureAnalysisConclusion conclusion = analysis != null
                ? analysis.getConclusion()
                : (timeline.failureObserved() ? null : FailureAnalysisConclusion.NO_FAILURE_OBSERVED);
        BigDecimal confidenceScore = analysis != null
                ? analysis.getConfidenceScore()
                : (timeline.failureObserved() ? null : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        String primaryServiceName = analysis != null ? analysis.getPrimaryServiceName() : null;
        String primaryEventId = analysis != null ? analysis.getPrimaryEventId() : null;

        List<FailureAnalysisCandidateResponse> candidateResponses = new ArrayList<>();
        if (analysis != null) {
            List<FailureAnalysisCandidate> candidates = candidateRepository
                    .findAllByAnalysisIdOrderByRankAsc(analysis.getAnalysisId());
            List<FailureAnalysisEvidence> allEvidence = evidenceRepository
                    .findAllByAnalysisIdOrderByCandidateRankAscSequenceNumberAsc(analysis.getAnalysisId());

            for (FailureAnalysisCandidate c : candidates) {
                List<FailureAnalysisEvidenceResponse> evList = allEvidence.stream()
                        .filter(e -> Objects.equals(e.getCandidateRank(), c.getRank()))
                        .map(FailureAnalysisEvidenceResponse::fromEntity)
                        .toList();
                candidateResponses.add(FailureAnalysisCandidateResponse.from(c, evList));
            }
        }

        // 5. Build consolidated evidence summary
        List<String> evidenceSummary = new ArrayList<>();
        for (FailureAnalysisCandidateResponse cr : candidateResponses) {
            evidenceSummary.add("Candidate #" + cr.rank() + " (" + cr.candidateType() + " in " + cr.serviceName()
                    + ", confidence=" + cr.confidenceScore() + "): " + cr.description());
            for (FailureAnalysisEvidenceResponse ev : cr.evidence()) {
                evidenceSummary.add("  - [" + ev.evidenceType() + "] " + ev.evidenceValue());
            }
        }

        if (verification != null) {
            evidenceSummary.add("Replay Verification (" + verification.getStatus() + "): " + verification.getResultMessage());
            for (ReplayVerificationDifference diff : differences) {
                evidenceSummary.add("  - Divergence at seq #" + diff.getSequenceNumber() + " ["
                        + diff.getDifferenceType() + "]: " + diff.getMessage());
            }
        }

        // 6. Build explicit investigation limitations
        List<String> limitations = new ArrayList<>();

        if (!timeline.failureObserved()) {
            limitations.add("No failure events were observed in the original telemetry trace.");
        }
        if (!analysisAvailable) {
            limitations.add("Failure analysis has not been performed for this trace; candidate root causes and confidence scores are unavailable.");
        }
        if (!scenarioAvailable) {
            limitations.add("Replay scenario has not been generated for this trace.");
        } else if (!executionAvailable) {
            limitations.add("Replay scenario has been created but simulation replay has not been executed.");
        }
        if (!verificationAvailable) {
            limitations.add("Replay verification has not been performed for this scenario.");
        } else {
            if (verificationStatus == ReplayVerificationStatus.FAILED) {
                limitations.add("Replay verification FAILED: simulated execution diverged from original failure telemetry.");
            } else if (verificationStatus == ReplayVerificationStatus.PARTIAL) {
                limitations.add("Replay verification PARTIAL: non-critical behavioral differences observed during simulation.");
            }
        }
        if (conclusion == FailureAnalysisConclusion.MULTIPLE_POSSIBLE_CAUSES) {
            limitations.add("Multiple possible causes identified with similar confidence scores; causal hierarchy is not definitively resolved.");
        }
        if (analysisStatus == FailureAnalysisStatus.INSUFFICIENT_EVIDENCE && timeline.failureObserved()) {
            limitations.add("Insufficient telemetry evidence to classify a specific root cause candidate.");
        }

        limitations.add("Telemetry timestamps reflect producer host clocks without vector clocks or hardware synchronization metadata; relative offsets measure elapsed duration from the earliest recorded event but do not correct for inter-service clock skew.");

        if (timelineService.hasCausalTimestampAnomaly(cleanTraceId)) {
            limitations.add("Potential cross-service clock anomaly detected: causal request/response event ordering exhibits non-monotonic timestamps across services.");
        }

        limitations.add("Analysis conclusions and candidates represent deterministic, evidence-backed correlations from observed telemetry and simulated replays; absolute real-world causality is not claimed.");

        long duration = System.nanoTime() - startNanos;
        metrics.recordInvestigationDuration("success", duration);
        log.info("Assembled incident investigation [traceId={}, analysisAvailable={}, verificationAvailable={}, candidates={}, durationMs={}]",
                cleanTraceId, analysisAvailable, verificationAvailable, candidateResponses.size(), duration / 1_000_000);

        return new IncidentInvestigationResponse(
                cleanTraceId,
                timeline,
                true, // correlationAvailable is true because telemetry trace exists
                scenarioAvailable,
                scenarioId,
                executionAvailable,
                executionStatus,
                verificationAvailable,
                verificationId,
                verificationStatus,
                verificationSummary,
                analysisAvailable,
                analysisId,
                analysisStatus,
                conclusion,
                confidenceScore,
                primaryServiceName,
                primaryEventId,
                candidateResponses,
                evidenceSummary,
                limitations
        );
    }
}
