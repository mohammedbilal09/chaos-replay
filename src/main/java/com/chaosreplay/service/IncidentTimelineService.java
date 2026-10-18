package com.chaosreplay.service;

import com.chaosreplay.api.dto.IncidentTimelineEntryResponse;
import com.chaosreplay.api.dto.IncidentTimelineResponse;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.FailureAnalysis;
import com.chaosreplay.domain.FailureAnalysisCandidate;
import com.chaosreplay.domain.FailureAnalysisConclusion;
import com.chaosreplay.domain.FailureAnalysisEvidence;
import com.chaosreplay.domain.FailureAnalysisStatus;
import com.chaosreplay.domain.FailureCandidateType;
import com.chaosreplay.domain.IncidentEntryCategory;
import com.chaosreplay.domain.ReplayScenario;
import com.chaosreplay.domain.ReplayVerification;
import com.chaosreplay.domain.ReplayVerificationDifference;
import com.chaosreplay.domain.Severity;
import com.chaosreplay.domain.TelemetryEvent;
import com.chaosreplay.exception.ResourceNotFoundException;
import com.chaosreplay.repository.FailureAnalysisCandidateRepository;
import com.chaosreplay.repository.FailureAnalysisEvidenceRepository;
import com.chaosreplay.repository.FailureAnalysisRepository;
import com.chaosreplay.repository.ReplayScenarioRepository;
import com.chaosreplay.repository.ReplayVerificationDifferenceRepository;
import com.chaosreplay.repository.ReplayVerificationRepository;
import com.chaosreplay.repository.TelemetryEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.chaosreplay.metrics.ChaosReplayMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Read-only service reconstructing deterministic, chronological incident timelines
 * from persisted telemetry and enriching entries with persisted analysis and verification evidence.
 */
@Service
public class IncidentTimelineService {

    private static final Logger log = LoggerFactory.getLogger(IncidentTimelineService.class);

    private final TelemetryEventRepository telemetryRepository;
    private final FailureAnalysisRepository analysisRepository;
    private final FailureAnalysisCandidateRepository candidateRepository;
    private final FailureAnalysisEvidenceRepository evidenceRepository;
    private final ReplayScenarioRepository scenarioRepository;
    private final ReplayVerificationRepository verificationRepository;
    private final ReplayVerificationDifferenceRepository differenceRepository;
    private final ChaosReplayMetrics metrics;

    public IncidentTimelineService(
            TelemetryEventRepository telemetryRepository,
            FailureAnalysisRepository analysisRepository,
            FailureAnalysisCandidateRepository candidateRepository,
            FailureAnalysisEvidenceRepository evidenceRepository,
            ReplayScenarioRepository scenarioRepository,
            ReplayVerificationRepository verificationRepository,
            ReplayVerificationDifferenceRepository differenceRepository
    ) {
        this(telemetryRepository, analysisRepository, candidateRepository, evidenceRepository,
                scenarioRepository, verificationRepository, differenceRepository,
                new ChaosReplayMetrics(new SimpleMeterRegistry()));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public IncidentTimelineService(
            TelemetryEventRepository telemetryRepository,
            FailureAnalysisRepository analysisRepository,
            FailureAnalysisCandidateRepository candidateRepository,
            FailureAnalysisEvidenceRepository evidenceRepository,
            ReplayScenarioRepository scenarioRepository,
            ReplayVerificationRepository verificationRepository,
            ReplayVerificationDifferenceRepository differenceRepository,
            ChaosReplayMetrics metrics
    ) {
        this.telemetryRepository = telemetryRepository;
        this.analysisRepository = analysisRepository;
        this.candidateRepository = candidateRepository;
        this.evidenceRepository = evidenceRepository;
        this.scenarioRepository = scenarioRepository;
        this.verificationRepository = verificationRepository;
        this.differenceRepository = differenceRepository;
        this.metrics = metrics != null ? metrics : new ChaosReplayMetrics(new SimpleMeterRegistry());
    }

    /**
     * Reconstructs a chronological, evidence-annotated incident timeline for a distributed trace.
     *
     * @param traceId distributed trace identifier
     * @return reconstructed timeline with diagnostic metrics and entry categories
     * @throws ResourceNotFoundException if trace has no telemetry events
     */
    @Transactional(readOnly = true)
    public IncidentTimelineResponse getTimeline(String traceId) {
        long startNanos = System.nanoTime();
        if (traceId == null || traceId.isBlank()) {
            metrics.recordTimelineDuration("not_found", System.nanoTime() - startNanos);
            throw new ResourceNotFoundException("Trace not found: " + traceId);
        }

        String cleanTraceId = traceId.trim();
        List<TelemetryEvent> events = telemetryRepository
                .findAllByTraceIdOrderByTimestampAscEventIdAsc(cleanTraceId);

        if (events.isEmpty()) {
            long duration = System.nanoTime() - startNanos;
            metrics.recordTimelineDuration("not_found", duration);
            log.info("No telemetry events found for traceId [{}] (durationMs={})", cleanTraceId, duration / 1_000_000);
            throw new ResourceNotFoundException("Trace not found: " + cleanTraceId);
        }

        int eventCount = events.size();
        Instant firstEventAt = events.get(0).getTimestamp();
        Instant lastEventAt = events.get(events.size() - 1).getTimestamp();
        long durationMs = Duration.between(firstEventAt, lastEventAt).toMillis();

        List<TelemetryEvent> failureEvents = events.stream()
                .filter(this::isFailure)
                .toList();
        int failureCount = failureEvents.size();
        boolean failureObserved = failureCount > 0;

        // Load optional latest failure analysis
        Optional<FailureAnalysis> optAnalysis = analysisRepository
                .findFirstByTraceIdOrderByCreatedAtDesc(cleanTraceId);
        boolean analysisAvailable = optAnalysis.isPresent();
        FailureAnalysis analysis = optAnalysis.orElse(null);

        List<FailureAnalysisCandidate> candidates = analysis != null
                ? candidateRepository.findAllByAnalysisIdOrderByRankAsc(analysis.getAnalysisId())
                : List.of();
        List<FailureAnalysisEvidence> allEvidence = analysis != null
                ? evidenceRepository.findAllByAnalysisIdOrderByCandidateRankAscSequenceNumberAsc(analysis.getAnalysisId())
                : List.of();

        // Load optional latest replay scenario and verification differences
        Optional<ReplayScenario> optScenario = scenarioRepository
                .findFirstBySourceTraceIdOrderByCreatedAtDesc(cleanTraceId);
        ReplayScenario scenario = optScenario.orElse(null);

        Optional<ReplayVerification> optVerification = scenario != null
                ? verificationRepository.findFirstByScenarioIdOrderByCreatedAtDesc(scenario.getScenarioId())
                : Optional.empty();
        boolean verificationAvailable = optVerification.isPresent();
        ReplayVerification verification = optVerification.orElse(null);

        List<ReplayVerificationDifference> differences = verification != null
                ? differenceRepository.findAllByVerificationIdOrderBySequenceNumberAsc(verification.getVerificationId())
                : List.of();

        FailureAnalysisStatus rootCauseStatus = null;
        FailureAnalysisConclusion conclusion = null;
        String primaryServiceName = null;
        String primaryEventId = null;
        BigDecimal confidenceScore = null;
        Instant generatedAt = analysis != null ? analysis.getCreatedAt() : lastEventAt;

        if (analysis != null) {
            rootCauseStatus = analysis.getStatus();
            conclusion = analysis.getConclusion();
            primaryServiceName = analysis.getPrimaryServiceName();
            primaryEventId = analysis.getPrimaryEventId();
            confidenceScore = analysis.getConfidenceScore();
        } else if (!failureObserved) {
            rootCauseStatus = FailureAnalysisStatus.INSUFFICIENT_EVIDENCE;
            conclusion = FailureAnalysisConclusion.NO_FAILURE_OBSERVED;
            confidenceScore = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }

        List<IncidentTimelineEntryResponse> timeline = new ArrayList<>();
        TelemetryEvent firstFailure = failureObserved ? failureEvents.get(0) : null;

        for (int i = 0; i < events.size(); i++) {
            TelemetryEvent event = events.get(i);
            int sequenceNumber = i + 1;
            long offsetMs = Duration.between(firstEventAt, event.getTimestamp()).toMillis();
            boolean eventFailed = isFailure(event);

            FailureAnalysisCandidate matchedCandidate = candidates.stream()
                    .filter(c -> Objects.equals(c.getEventId(), event.getEventId()))
                    .findFirst()
                    .orElse(null);

            Integer relatedCandidateRank = matchedCandidate != null ? matchedCandidate.getRank() : null;

            List<String> evidenceRefs = new ArrayList<>();
            if (matchedCandidate != null) {
                allEvidence.stream()
                        .filter(e -> Objects.equals(e.getCandidateRank(), matchedCandidate.getRank()))
                        .map(e -> e.getEvidenceType().name() + ": " + e.getEvidenceValue())
                        .forEach(evidenceRefs::add);
            }

            differences.stream()
                    .filter(d -> Objects.equals(d.getExpectedEventId(), event.getEventId())
                            || Objects.equals(d.getActualEventId(), event.getEventId()))
                    .map(d -> "REPLAY_DIVERGENCE: " + d.getDifferenceType().name() + " - " + d.getMessage())
                    .forEach(evidenceRefs::add);

            boolean failurePropagation = false;
            if (eventFailed && firstFailure != null && !Objects.equals(event.getEventId(), firstFailure.getEventId())) {
                boolean hasPropagationEvidence = evidenceRefs.stream()
                        .anyMatch(r -> r.contains("SERVICE_PROPAGATION") || r.contains("DOWNSTREAM_ERROR"));
                if (hasPropagationEvidence || (matchedCandidate != null && matchedCandidate.getCandidateType() == FailureCandidateType.DOWNSTREAM_FAILURE)) {
                    failurePropagation = true;
                }
            }

            IncidentEntryCategory entryCategory = determineEntryCategory(
                    event,
                    eventFailed,
                    matchedCandidate,
                    evidenceRefs,
                    firstFailure,
                    differences
            );

            timeline.add(new IncidentTimelineEntryResponse(
                    sequenceNumber,
                    event.getEventId(),
                    event.getTimestamp(),
                    offsetMs,
                    event.getServiceName(),
                    event.getServiceInstance(),
                    event.getEventType(),
                    event.getSeverity(),
                    event.getOperation(),
                    event.getMessage(),
                    entryCategory,
                    eventFailed,
                    failurePropagation,
                    relatedCandidateRank,
                    evidenceRefs
            ));
        }

        long duration = System.nanoTime() - startNanos;
        metrics.recordTimelineDuration("success", duration);
        log.info("Reconstructed incident timeline [traceId={}, events={}, failures={}, durationMs={}]",
                cleanTraceId, eventCount, failureCount, duration / 1_000_000);

        return new IncidentTimelineResponse(
                cleanTraceId,
                eventCount,
                failureCount,
                failureObserved,
                firstEventAt,
                lastEventAt,
                durationMs,
                analysisAvailable,
                verificationAvailable,
                rootCauseStatus,
                conclusion,
                primaryServiceName,
                primaryEventId,
                confidenceScore,
                timeline,
                generatedAt
        );
    }

    private IncidentEntryCategory determineEntryCategory(
            TelemetryEvent event,
            boolean eventFailed,
            FailureAnalysisCandidate candidate,
            List<String> evidenceRefs,
            TelemetryEvent firstFailure,
            List<ReplayVerificationDifference> differences
    ) {
        boolean hasReplayDiff = differences.stream()
                .anyMatch(d -> Objects.equals(d.getExpectedEventId(), event.getEventId())
                        || Objects.equals(d.getActualEventId(), event.getEventId()));

        if (!eventFailed) {
            if (hasReplayDiff) {
                return IncidentEntryCategory.REPLAY_DIVERGENCE;
            }
            return IncidentEntryCategory.NORMAL_EVENT;
        }

        if (candidate != null) {
            switch (candidate.getCandidateType()) {
                case DATABASE_FAILURE -> { return IncidentEntryCategory.DATABASE_FAILURE; }
                case EXTERNAL_DEPENDENCY_FAILURE -> { return IncidentEntryCategory.EXTERNAL_CALL_FAILURE; }
                case TIMEOUT -> { return IncidentEntryCategory.TIMEOUT_SIGNAL; }
                case DOWNSTREAM_FAILURE -> { return IncidentEntryCategory.DOWNSTREAM_ERROR; }
                default -> {}
            }
        }

        if (evidenceRefs.stream().anyMatch(r -> r.contains("DATABASE_ERROR"))) {
            return IncidentEntryCategory.DATABASE_FAILURE;
        }
        if (evidenceRefs.stream().anyMatch(r -> r.contains("EXTERNAL_CALL_ERROR"))) {
            return IncidentEntryCategory.EXTERNAL_CALL_FAILURE;
        }
        if (evidenceRefs.stream().anyMatch(r -> r.contains("TIMEOUT_SIGNAL"))) {
            return IncidentEntryCategory.TIMEOUT_SIGNAL;
        }
        if (evidenceRefs.stream().anyMatch(r -> r.contains("DOWNSTREAM_ERROR") || r.contains("SERVICE_PROPAGATION"))) {
            return IncidentEntryCategory.DOWNSTREAM_ERROR;
        }

        if (event.getEventType() == EventType.DATABASE) {
            return IncidentEntryCategory.DATABASE_FAILURE;
        }
        if (event.getEventType() == EventType.EXTERNAL_CALL) {
            return IncidentEntryCategory.EXTERNAL_CALL_FAILURE;
        }
        if (hasTimeoutSignal(event)) {
            return IncidentEntryCategory.TIMEOUT_SIGNAL;
        }
        if (firstFailure != null && !Objects.equals(event.getEventId(), firstFailure.getEventId())
                && !Objects.equals(event.getServiceName(), firstFailure.getServiceName())) {
            return IncidentEntryCategory.DOWNSTREAM_ERROR;
        }
        if (event.getSeverity() == Severity.FATAL) {
            return IncidentEntryCategory.FATAL_EVENT;
        }

        return IncidentEntryCategory.ERROR_EVENT;
    }

    private boolean isFailure(TelemetryEvent event) {
        return event.getSeverity() == Severity.ERROR
                || event.getSeverity() == Severity.FATAL
                || event.getEventType() == EventType.ERROR;
    }

    private boolean hasTimeoutSignal(TelemetryEvent event) {
        String msg = event.getMessage() != null ? event.getMessage().toLowerCase(Locale.ROOT) : "";
        String op = event.getOperation() != null ? event.getOperation().toLowerCase(Locale.ROOT) : "";

        if (msg.contains("timeout") || msg.contains("timed out") || msg.contains("deadline exceeded")
                || op.contains("timeout") || op.contains("timed out") || op.contains("deadline exceeded")) {
            return true;
        }

        Map<String, Object> meta = event.getMetadata();
        if (meta != null) {
            for (Map.Entry<String, Object> entry : meta.entrySet()) {
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                if (key.contains("timeout") || key.contains("timeoutms")) {
                    return true;
                }
                String val = String.valueOf(entry.getValue()).toLowerCase(Locale.ROOT);
                if (val.contains("timeout") || val.contains("timed out") || val.contains("deadline exceeded")) {
                    return true;
                }
            }
        }
        return false;
    }
}
