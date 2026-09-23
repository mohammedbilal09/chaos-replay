package com.chaosreplay.service;

import com.chaosreplay.api.dto.FailureAnalysisCandidateResponse;
import com.chaosreplay.api.dto.FailureAnalysisEvidenceResponse;
import com.chaosreplay.api.dto.FailureAnalysisResponse;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.FailureAnalysis;
import com.chaosreplay.domain.FailureAnalysisCandidate;
import com.chaosreplay.domain.FailureAnalysisConclusion;
import com.chaosreplay.domain.FailureAnalysisEvidence;
import com.chaosreplay.domain.FailureAnalysisStatus;
import com.chaosreplay.domain.FailureCandidateType;
import com.chaosreplay.domain.FailureEvidenceType;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Service providing deterministic, evidence-backed failure analysis across correlated telemetry,
 * failure reconstructions, replay scenarios, and verification differences.
 * Strictly evidence-grounded: never uses generative AI or probabilistic heuristics.
 */
@Service
public class FailureAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(FailureAnalysisService.class);

    private final TelemetryEventRepository telemetryRepository;
    private final ReplayScenarioRepository scenarioRepository;
    private final ReplayVerificationRepository verificationRepository;
    private final ReplayVerificationDifferenceRepository differenceRepository;
    private final FailureAnalysisRepository analysisRepository;
    private final FailureAnalysisCandidateRepository candidateRepository;
    private final FailureAnalysisEvidenceRepository evidenceRepository;

    public FailureAnalysisService(
            TelemetryEventRepository telemetryRepository,
            ReplayScenarioRepository scenarioRepository,
            ReplayVerificationRepository verificationRepository,
            ReplayVerificationDifferenceRepository differenceRepository,
            FailureAnalysisRepository analysisRepository,
            FailureAnalysisCandidateRepository candidateRepository,
            FailureAnalysisEvidenceRepository evidenceRepository
    ) {
        this.telemetryRepository = telemetryRepository;
        this.scenarioRepository = scenarioRepository;
        this.verificationRepository = verificationRepository;
        this.differenceRepository = differenceRepository;
        this.analysisRepository = analysisRepository;
        this.candidateRepository = candidateRepository;
        this.evidenceRepository = evidenceRepository;
    }

    /**
     * Performs a deterministic, evidence-backed failure analysis for a given trace identifier.
     * Idempotent: returns existing analysis if an identical evidence hash already exists.
     *
     * @param traceId distributed trace identifier
     * @return analysis result containing response DTO and newlyCreated flag
     * @throws ResourceNotFoundException if no telemetry exists for the trace
     */
    @Transactional
    public AnalysisResult analyzeTrace(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new ResourceNotFoundException("Trace not found: " + traceId);
        }

        String cleanTraceId = traceId.trim();
        List<TelemetryEvent> events = telemetryRepository
                .findAllByTraceIdOrderByTimestampAscEventIdAsc(cleanTraceId);

        if (events.isEmpty()) {
            log.info("Cannot perform failure analysis: no telemetry events found for traceId [{}]", cleanTraceId);
            throw new ResourceNotFoundException("Trace not found: " + cleanTraceId);
        }

        // 1. Retrieve associated scenario if one exists
        Optional<ReplayScenario> scenarioOpt = scenarioRepository
                .findFirstBySourceTraceIdOrderByCreatedAtAsc(cleanTraceId);
        String scenarioId = scenarioOpt.map(ReplayScenario::getScenarioId).orElse(null);

        // 2. Retrieve associated verification and differences if available
        String verificationId = null;
        List<ReplayVerificationDifference> differences = List.of();
        if (scenarioId != null) {
            List<ReplayVerification> verifications = verificationRepository
                    .findAllByScenarioIdOrderByCreatedAtDesc(scenarioId);
            if (!verifications.isEmpty()) {
                ReplayVerification latestVerification = verifications.get(0);
                verificationId = latestVerification.getVerificationId();
                differences = differenceRepository
                        .findAllByVerificationIdOrderBySequenceNumberAsc(verificationId);
            }
        }

        // 3. Compute deterministic analysis ID
        String analysisId = generateDeterministicAnalysisId(
                cleanTraceId,
                events,
                scenarioId,
                verificationId,
                differences
        );

        // 4. Idempotency check: return existing analysis if already performed
        Optional<FailureAnalysis> existingOpt = analysisRepository.findByAnalysisId(analysisId);
        if (existingOpt.isPresent()) {
            FailureAnalysis existing = existingOpt.get();
            log.info("Idempotent failure analysis retrieved for trace [{}] with analysisId [{}]",
                    cleanTraceId, analysisId);
            List<FailureAnalysisCandidateResponse> candidateResponses = loadCandidateResponses(analysisId);
            return new AnalysisResult(FailureAnalysisResponse.from(existing, candidateResponses), false);
        }

        log.info("Executing deterministic failure analysis for trace [{}] ({} events, scenario={}, verification={})",
                cleanTraceId, events.size(), scenarioId, verificationId);

        int eventCount = events.size();
        List<TelemetryEvent> failureEvents = events.stream()
                .filter(this::isFailure)
                .toList();
        int failureCount = failureEvents.size();

        // 5. Handle No-Failure Case
        if (failureCount == 0) {
            FailureAnalysis noFailureAnalysis = new FailureAnalysis(
                    analysisId,
                    cleanTraceId,
                    scenarioId,
                    verificationId,
                    FailureAnalysisStatus.INSUFFICIENT_EVIDENCE,
                    FailureAnalysisConclusion.NO_FAILURE_OBSERVED,
                    BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                    eventCount,
                    0,
                    0,
                    null,
                    null,
                    "No failure events were observed in the trace."
            );
            FailureAnalysis saved = analysisRepository.saveAndFlush(noFailureAnalysis);
            return new AnalysisResult(FailureAnalysisResponse.from(saved, List.of()), true);
        }

        // 6. Generate Candidates and Evidence from observed failure events
        TelemetryEvent firstFailure = failureEvents.get(0);
        List<CandidateDraft> candidateDrafts = new ArrayList<>();

        for (int i = 0; i < failureEvents.size(); i++) {
            TelemetryEvent fe = failureEvents.get(i);
            int seqNum = findSequenceNumber(events, fe.getEventId());

            FailureCandidateType candidateType = classifyCandidate(fe, firstFailure, i > 0);
            List<EvidenceDraft> evidenceList = generateEvidence(fe, firstFailure, candidateType, seqNum, differences);
            BigDecimal confidenceScore = calculateConfidence(candidateType, evidenceList, fe);
            String description = generateDescription(fe, candidateType, fe.getEventId().equals(firstFailure.getEventId()));

            candidateDrafts.add(new CandidateDraft(
                    fe,
                    seqNum,
                    candidateType,
                    confidenceScore,
                    description,
                    evidenceList
            ));
        }

        // 7. Deterministic Ranking
        candidateDrafts.sort(Comparator
                .comparing(CandidateDraft::confidenceScore, Comparator.reverseOrder())
                .thenComparing(cd -> cd.event().getTimestamp())
                .thenComparingInt(CandidateDraft::sequenceNumber)
                .thenComparing(cd -> cd.event().getEventId())
                .thenComparing(cd -> cd.event().getServiceName())
                .thenComparing(cd -> cd.candidateType().name())
        );

        int candidateCount = candidateDrafts.size();
        CandidateDraft primaryCandidate = candidateDrafts.get(0);
        String primaryServiceName = primaryCandidate.event().getServiceName();
        String primaryEventId = primaryCandidate.event().getEventId();
        BigDecimal primaryConfidence = primaryCandidate.confidenceScore();

        // 8. Determine Conclusion
        FailureAnalysisConclusion conclusion;
        if (candidateCount == 1) {
            conclusion = FailureAnalysisConclusion.ROOT_CAUSE_CANDIDATE;
        } else {
            CandidateDraft runnerUp = candidateDrafts.get(1);
            BigDecimal scoreDiff = primaryConfidence.subtract(runnerUp.confidenceScore());
            if (scoreDiff.compareTo(new BigDecimal("0.0500")) >= 0) {
                conclusion = FailureAnalysisConclusion.ROOT_CAUSE_CANDIDATE;
            } else {
                conclusion = FailureAnalysisConclusion.MULTIPLE_POSSIBLE_CAUSES;
            }
        }

        // 9. Generate Summary
        String summary = generateSummary(primaryCandidate, candidateCount, conclusion);

        FailureAnalysis analysis = new FailureAnalysis(
                analysisId,
                cleanTraceId,
                scenarioId,
                verificationId,
                FailureAnalysisStatus.COMPLETED,
                conclusion,
                primaryConfidence,
                eventCount,
                failureCount,
                candidateCount,
                primaryServiceName,
                primaryEventId,
                summary
        );

        FailureAnalysis savedAnalysis = analysisRepository.saveAndFlush(analysis);

        // 10. Persist Candidates and Evidence
        List<FailureAnalysisCandidateResponse> candidateResponses = new ArrayList<>();

        for (int rank = 1; rank <= candidateDrafts.size(); rank++) {
            CandidateDraft draft = candidateDrafts.get(rank - 1);
            TelemetryEvent fe = draft.event();

            FailureAnalysisCandidate candidateEntity = new FailureAnalysisCandidate(
                    analysisId,
                    rank,
                    fe.getServiceName(),
                    fe.getEventId(),
                    fe.getEventType() != null ? fe.getEventType().name() : null,
                    fe.getSeverity() != null ? fe.getSeverity().name() : null,
                    draft.candidateType(),
                    draft.confidenceScore(),
                    fe.getTimestamp(),
                    draft.description()
            );

            candidateRepository.saveAndFlush(candidateEntity);

            List<FailureAnalysisEvidence> evidenceEntities = new ArrayList<>();
            List<FailureAnalysisEvidenceResponse> evidenceResponses = new ArrayList<>();

            for (EvidenceDraft evDraft : draft.evidenceList()) {
                FailureAnalysisEvidence evEntity = new FailureAnalysisEvidence(
                        analysisId,
                        rank,
                        draft.sequenceNumber(),
                        fe.getEventId(),
                        evDraft.type(),
                        evDraft.value()
                );
                evidenceEntities.add(evEntity);
                evidenceResponses.add(new FailureAnalysisEvidenceResponse(
                        draft.sequenceNumber(),
                        fe.getEventId(),
                        evDraft.type(),
                        evDraft.value()
                ));
            }

            evidenceRepository.saveAllAndFlush(evidenceEntities);

            candidateResponses.add(FailureAnalysisCandidateResponse.from(candidateEntity, evidenceResponses));
        }

        log.info("Persisted failure analysis [{}] for trace [{}] with conclusion [{}] and {} candidates",
                analysisId, cleanTraceId, conclusion, candidateCount);

        return new AnalysisResult(FailureAnalysisResponse.from(savedAnalysis, candidateResponses), true);
    }

    /**
     * Retrieves an existing failure analysis by its deterministic analysis identifier.
     *
     * @param analysisId deterministic analysis identifier
     * @return analysis response with candidates and evidence
     * @throws ResourceNotFoundException if analysis is not found
     */
    @Transactional(readOnly = true)
    public FailureAnalysisResponse getAnalysis(String analysisId) {
        if (analysisId == null || analysisId.isBlank()) {
            throw new ResourceNotFoundException("Analysis not found: " + analysisId);
        }

        String cleanAnalysisId = analysisId.trim();
        FailureAnalysis analysis = analysisRepository.findByAnalysisId(cleanAnalysisId)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis not found: " + cleanAnalysisId));

        List<FailureAnalysisCandidateResponse> candidates = loadCandidateResponses(cleanAnalysisId);
        return FailureAnalysisResponse.from(analysis, candidates);
    }

    /**
     * Retrieves the latest failure analysis associated with a trace.
     *
     * @param traceId distributed trace identifier
     * @return latest analysis response
     * @throws ResourceNotFoundException if no analysis exists for trace
     */
    @Transactional(readOnly = true)
    public FailureAnalysisResponse getLatestAnalysisForTrace(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new ResourceNotFoundException("Trace not found: " + traceId);
        }

        String cleanTraceId = traceId.trim();
        FailureAnalysis analysis = analysisRepository.findFirstByTraceIdOrderByCreatedAtDesc(cleanTraceId)
                .orElseThrow(() -> new ResourceNotFoundException("No failure analysis found for trace: " + cleanTraceId));

        List<FailureAnalysisCandidateResponse> candidates = loadCandidateResponses(analysis.getAnalysisId());
        return FailureAnalysisResponse.from(analysis, candidates);
    }

    /**
     * Retrieves the complete analysis history for a trace ordered by creation time descending.
     *
     * @param traceId distributed trace identifier
     * @return list of analysis responses
     * @throws ResourceNotFoundException if no analysis exists for trace
     */
    @Transactional(readOnly = true)
    public List<FailureAnalysisResponse> getAnalysisHistoryForTrace(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new ResourceNotFoundException("Trace not found: " + traceId);
        }

        String cleanTraceId = traceId.trim();
        List<FailureAnalysis> analyses = analysisRepository.findAllByTraceIdOrderByCreatedAtDesc(cleanTraceId);
        if (analyses.isEmpty()) {
            throw new ResourceNotFoundException("No failure analysis history found for trace: " + cleanTraceId);
        }

        return analyses.stream()
                .map(a -> {
                    List<FailureAnalysisCandidateResponse> candidates = loadCandidateResponses(a.getAnalysisId());
                    return FailureAnalysisResponse.from(a, candidates);
                })
                .toList();
    }

    private List<FailureAnalysisCandidateResponse> loadCandidateResponses(String analysisId) {
        List<FailureAnalysisCandidate> candidates = candidateRepository
                .findAllByAnalysisIdOrderByRankAsc(analysisId);

        List<FailureAnalysisEvidence> allEvidence = evidenceRepository
                .findAllByAnalysisIdOrderByCandidateRankAscSequenceNumberAsc(analysisId);

        return candidates.stream()
                .map(candidate -> {
                    List<FailureAnalysisEvidenceResponse> evidenceResponses = allEvidence.stream()
                            .filter(e -> e.getCandidateRank() == candidate.getRank())
                            .map(FailureAnalysisEvidenceResponse::fromEntity)
                            .toList();
                    return FailureAnalysisCandidateResponse.from(candidate, evidenceResponses);
                })
                .toList();
    }

    private FailureCandidateType classifyCandidate(
            TelemetryEvent event,
            TelemetryEvent firstFailure,
            boolean isSubsequentFailure
    ) {
        if (event.getEventType() == EventType.DATABASE && isFailureSeverity(event.getSeverity())) {
            return FailureCandidateType.DATABASE_FAILURE;
        }

        if (event.getEventType() == EventType.EXTERNAL_CALL && isFailureSeverity(event.getSeverity())) {
            return FailureCandidateType.EXTERNAL_DEPENDENCY_FAILURE;
        }

        if (hasTimeoutSignal(event)) {
            return FailureCandidateType.TIMEOUT;
        }

        if (event.getEventType() == EventType.ERROR) {
            return FailureCandidateType.APPLICATION_ERROR;
        }

        if (isSubsequentFailure && !Objects.equals(event.getServiceName(), firstFailure.getServiceName())) {
            return FailureCandidateType.DOWNSTREAM_FAILURE;
        }

        if (isFailureSeverity(event.getSeverity())) {
            return FailureCandidateType.SERVICE_FAILURE;
        }

        return FailureCandidateType.UNKNOWN_FAILURE;
    }

    private List<EvidenceDraft> generateEvidence(
            TelemetryEvent event,
            TelemetryEvent firstFailure,
            FailureCandidateType candidateType,
            int sequenceNumber,
            List<ReplayVerificationDifference> differences
    ) {
        List<EvidenceDraft> evidence = new ArrayList<>();
        boolean isFirst = Objects.equals(event.getEventId(), firstFailure.getEventId());

        if (isFirst) {
            evidence.add(new EvidenceDraft(
                    FailureEvidenceType.FIRST_FAILURE,
                    "Earliest failure observed in trace execution at sequence #" + sequenceNumber
            ));
            evidence.add(new EvidenceDraft(
                    FailureEvidenceType.TEMPORAL_PRECEDENCE,
                    "Event preceded all subsequent failures in trace"
            ));
        }

        if (event.getSeverity() == Severity.ERROR) {
            evidence.add(new EvidenceDraft(
                    FailureEvidenceType.ERROR_EVENT,
                    "Event logged with ERROR severity"
            ));
        } else if (event.getSeverity() == Severity.FATAL) {
            evidence.add(new EvidenceDraft(
                    FailureEvidenceType.FATAL_EVENT,
                    "Event logged with FATAL severity"
            ));
        }

        if (candidateType == FailureCandidateType.DATABASE_FAILURE) {
            evidence.add(new EvidenceDraft(
                    FailureEvidenceType.DATABASE_ERROR,
                    "Database operation reported failure: " + event.getOperation()
            ));
        } else if (candidateType == FailureCandidateType.EXTERNAL_DEPENDENCY_FAILURE) {
            evidence.add(new EvidenceDraft(
                    FailureEvidenceType.EXTERNAL_CALL_ERROR,
                    "Downstream external call reported failure: " + event.getOperation()
            ));
        } else if (candidateType == FailureCandidateType.TIMEOUT) {
            evidence.add(new EvidenceDraft(
                    FailureEvidenceType.TIMEOUT_SIGNAL,
                    "Explicit timeout indicator identified in event message or metadata"
            ));
        } else if (candidateType == FailureCandidateType.DOWNSTREAM_FAILURE) {
            evidence.add(new EvidenceDraft(
                    FailureEvidenceType.DOWNSTREAM_ERROR,
                    "Failure occurred in downstream service after initial failure in " + firstFailure.getServiceName()
            ));
            evidence.add(new EvidenceDraft(
                    FailureEvidenceType.SERVICE_PROPAGATION,
                    "Potential downstream propagation from " + firstFailure.getServiceName() + " to " + event.getServiceName()
            ));
            evidence.add(new EvidenceDraft(
                    FailureEvidenceType.TEMPORAL_PRECEDENCE,
                    "Event followed earlier failure event " + firstFailure.getEventId()
            ));
        }

        // Replay verification integration
        boolean hasReplayMismatch = differences.stream().anyMatch(d ->
                Objects.equals(d.getExpectedEventId(), event.getEventId())
                        || Objects.equals(d.getActualEventId(), event.getEventId())
                        || Objects.equals(d.getExpectedServiceName(), event.getServiceName())
                        || Objects.equals(d.getActualServiceName(), event.getServiceName())
        );

        if (hasReplayMismatch) {
            evidence.add(new EvidenceDraft(
                    FailureEvidenceType.REPLAY_MISMATCH,
                    "Replay verification detected behavioral divergence associated with this event/service"
            ));
        }

        return evidence;
    }

    private BigDecimal calculateConfidence(
            FailureCandidateType candidateType,
            List<EvidenceDraft> evidenceList,
            TelemetryEvent event
    ) {
        double baseScore = switch (candidateType) {
            case DATABASE_FAILURE -> 0.85;
            case EXTERNAL_DEPENDENCY_FAILURE -> 0.85;
            case TIMEOUT -> 0.80;
            case APPLICATION_ERROR -> 0.70;
            case SERVICE_FAILURE -> 0.65;
            case DOWNSTREAM_FAILURE -> 0.55;
            case UNKNOWN_FAILURE -> 0.30;
        };

        Set<FailureEvidenceType> types = new HashSet<>();
        for (EvidenceDraft ev : evidenceList) {
            types.add(ev.type());
        }

        double bonus = 0.0;
        if (types.contains(FailureEvidenceType.FIRST_FAILURE)) {
            bonus += 0.05;
        }
        if (types.contains(FailureEvidenceType.REPLAY_MISMATCH)) {
            bonus += 0.05;
        }
        if (types.contains(FailureEvidenceType.TEMPORAL_PRECEDENCE)) {
            bonus += 0.03;
        }
        if (hasExplicitMetadata(event)) {
            bonus += 0.03;
        }

        double total = Math.min(1.0, Math.max(0.0, baseScore + bonus));
        return BigDecimal.valueOf(total).setScale(4, RoundingMode.HALF_UP);
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

    private boolean hasExplicitMetadata(TelemetryEvent event) {
        Map<String, Object> meta = event.getMetadata();
        if (meta == null || meta.isEmpty()) {
            return false;
        }
        return meta.containsKey("timeoutMs")
                || meta.containsKey("provider")
                || meta.containsKey("error")
                || meta.containsKey("exception")
                || meta.containsKey("retryCount");
    }

    private String generateDescription(TelemetryEvent event, FailureCandidateType type, boolean isFirst) {
        String base = type.name() + " in " + event.getServiceName() + " at event " + event.getEventId();
        if (isFirst) {
            return base + ". First failure observed in trace execution.";
        } else {
            return base + ". Observed subsequently during trace execution.";
        }
    }

    private String generateSummary(
            CandidateDraft primary,
            int candidateCount,
            FailureAnalysisConclusion conclusion
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Primary failure candidate: ").append(primary.candidateType()).append(" in ")
                .append(primary.event().getServiceName()).append(" at event ").append(primary.event().getEventId()).append(".");

        if (primary.candidateType() == FailureCandidateType.DATABASE_FAILURE) {
            sb.append(" Database operation '").append(primary.event().getOperation())
                    .append("' reported failure.");
        } else if (primary.candidateType() == FailureCandidateType.EXTERNAL_DEPENDENCY_FAILURE) {
            sb.append(" External dependency call '").append(primary.event().getOperation())
                    .append("' failed.");
        } else if (primary.candidateType() == FailureCandidateType.TIMEOUT) {
            sb.append(" Explicit timeout signal identified in operation '").append(primary.event().getOperation()).append("'.");
        }

        if (candidateCount > 1) {
            sb.append(" Evaluated ").append(candidateCount).append(" candidate causes with conclusion: ")
                    .append(conclusion).append(".");
        } else {
            sb.append(" Single failure candidate identified with conclusion: ").append(conclusion).append(".");
        }

        return sb.toString();
    }

    private int findSequenceNumber(List<TelemetryEvent> events, String eventId) {
        for (int i = 0; i < events.size(); i++) {
            if (Objects.equals(events.get(i).getEventId(), eventId)) {
                return i + 1;
            }
        }
        return 0;
    }

    private boolean isFailure(TelemetryEvent event) {
        return event.getSeverity() == Severity.ERROR
                || event.getSeverity() == Severity.FATAL
                || event.getEventType() == EventType.ERROR;
    }

    private boolean isFailureSeverity(Severity severity) {
        return severity == Severity.ERROR || severity == Severity.FATAL;
    }

    /**
     * Generates a deterministic SHA-256 analysis identifier based on the complete ordered input set.
     */
    private String generateDeterministicAnalysisId(
            String traceId,
            List<TelemetryEvent> events,
            String scenarioId,
            String verificationId,
            List<ReplayVerificationDifference> differences
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            sb.append("trace:").append(traceId).append(";events:");

            for (TelemetryEvent ev : events) {
                sb.append(ev.getEventId()).append(",")
                        .append(ev.getTimestamp().toEpochMilli()).append(",")
                        .append(ev.getServiceName()).append(",")
                        .append(ev.getEventType()).append(",")
                        .append(ev.getSeverity()).append(",")
                        .append(ev.getOperation() != null ? ev.getOperation() : "").append(";");
            }

            sb.append("scenario:").append(scenarioId != null ? scenarioId : "none").append(";");
            sb.append("verification:").append(verificationId != null ? verificationId : "none").append(";");

            sb.append("diffs:");
            for (ReplayVerificationDifference diff : differences) {
                sb.append(diff.getSequenceNumber()).append(",")
                        .append(diff.getDifferenceType()).append(",")
                        .append(diff.getExpectedEventId() != null ? diff.getExpectedEventId() : "").append(",")
                        .append(diff.getActualEventId() != null ? diff.getActualEventId() : "").append(";");
            }

            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return "analysis-" + hexString;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public record AnalysisResult(
            FailureAnalysisResponse response,
            boolean newlyCreated
    ) {
    }

    private record EvidenceDraft(
            FailureEvidenceType type,
            String value
    ) {
    }

    private record CandidateDraft(
            TelemetryEvent event,
            int sequenceNumber,
            FailureCandidateType candidateType,
            BigDecimal confidenceScore,
            String description,
            List<EvidenceDraft> evidenceList
    ) {
    }
}
