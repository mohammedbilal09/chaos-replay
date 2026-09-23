package com.chaosreplay.service;

import com.chaosreplay.api.dto.ReplayVerificationResponse;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.ReplayObservedEvent;
import com.chaosreplay.domain.ReplayScenario;
import com.chaosreplay.domain.ReplayScenarioEvent;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service providing deterministic failure replay verification and behavioral comparison.
 * Compares persisted ReplayScenarioEvent snapshots against simulated ReplayObservedEvent observations.
 * Verification is strictly simulation-only and preserves the immutability of TelemetryEvent and ReplayScenario.
 */
@Service
public class ReplayVerificationService {

    private static final Logger log = LoggerFactory.getLogger(ReplayVerificationService.class);

    private final ReplayScenarioRepository scenarioRepository;
    private final ReplayScenarioEventRepository scenarioEventRepository;
    private final ReplayVerificationRepository verificationRepository;
    private final ReplayVerificationDifferenceRepository differenceRepository;

    public ReplayVerificationService(
            ReplayScenarioRepository scenarioRepository,
            ReplayScenarioEventRepository scenarioEventRepository,
            ReplayVerificationRepository verificationRepository,
            ReplayVerificationDifferenceRepository differenceRepository
    ) {
        this.scenarioRepository = scenarioRepository;
        this.scenarioEventRepository = scenarioEventRepository;
        this.verificationRepository = verificationRepository;
        this.differenceRepository = differenceRepository;
    }

    /**
     * Verifies a replay scenario using default deterministic simulation observations.
     * Idempotent: returns existing verification if identical verificationId exists.
     *
     * @param scenarioId deterministic scenario identifier
     * @return verification result with newlyCreated status
     * @throws ResourceNotFoundException if scenario is not found
     */
    @Transactional
    public ReplayVerificationResult verifyScenario(String scenarioId) {
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new ResourceNotFoundException("Scenario not found with id: " + scenarioId);
        }

        String cleanScenarioId = scenarioId.trim();
        ReplayScenario scenario = scenarioRepository.findByScenarioId(cleanScenarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Scenario not found with id: " + cleanScenarioId));

        List<ReplayScenarioEvent> expectedEvents = scenarioEventRepository
                .findAllByScenarioIdOrderBySequenceNumberAsc(cleanScenarioId);

        // Generate baseline simulated observations from scenario event snapshots
        List<ReplayObservedEvent> defaultObservations = expectedEvents.stream()
                .map(event -> new ReplayObservedEvent(
                        event.getSequenceNumber(),
                        event.getEventId(),
                        event.getServiceName(),
                        event.getEventType(),
                        event.getSeverity(),
                        event.getOffsetMs()
                ))
                .toList();

        return verifyScenarioWithObservations(cleanScenarioId, defaultObservations, scenario.getDurationMs());
    }

    /**
     * Verifies a replay scenario against custom or synthetic observations.
     * Enables deterministic evaluation of all behavioral difference paths (missing events, unexpected events, mismatches).
     *
     * @param scenarioId        deterministic scenario identifier
     * @param observedEvents    list of observed events from replay execution
     * @param replayedDurationMs total observed replay duration in milliseconds
     * @return verification result with newlyCreated status
     * @throws ResourceNotFoundException if scenario is not found
     */
    @Transactional
    public ReplayVerificationResult verifyScenarioWithObservations(
            String scenarioId,
            List<ReplayObservedEvent> observedEvents,
            long replayedDurationMs
    ) {
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new ResourceNotFoundException("Scenario not found with id: " + scenarioId);
        }

        String cleanScenarioId = scenarioId.trim();
        ReplayScenario scenario = scenarioRepository.findByScenarioId(cleanScenarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Scenario not found with id: " + cleanScenarioId));

        List<ReplayScenarioEvent> expectedEvents = scenarioEventRepository
                .findAllByScenarioIdOrderBySequenceNumberAsc(cleanScenarioId);

        List<ReplayObservedEvent> safeObserved = observedEvents != null ? observedEvents : List.of();

        String verificationId = generateDeterministicVerificationId(
                cleanScenarioId,
                expectedEvents,
                safeObserved,
                replayedDurationMs
        );

        // Idempotency check: return existing verification if already performed
        var existingOpt = verificationRepository.findByVerificationId(verificationId);
        if (existingOpt.isPresent()) {
            ReplayVerification existing = existingOpt.get();
            List<ReplayVerificationDifference> diffs = differenceRepository
                    .findAllByVerificationIdOrderBySequenceNumberAsc(verificationId);
            log.info("Idempotent replay verification retrieved for scenario [{}] with verificationId [{}]",
                    cleanScenarioId, verificationId);
            return new ReplayVerificationResult(ReplayVerificationResponse.from(existing, diffs), false);
        }

        log.info("Executing deterministic replay verification for scenario [{}] (expected={}, observed={})",
                cleanScenarioId, expectedEvents.size(), safeObserved.size());

        // Map expected and observed events by sequenceNumber
        Map<Integer, ReplayScenarioEvent> expectedBySeq = expectedEvents.stream()
                .collect(Collectors.toMap(ReplayScenarioEvent::getSequenceNumber, Function.identity(), (a, b) -> a));

        Map<Integer, ReplayObservedEvent> observedBySeq = safeObserved.stream()
                .collect(Collectors.toMap(ReplayObservedEvent::sequenceNumber, Function.identity(), (a, b) -> a));

        Set<Integer> allSequences = new TreeSet<>();
        allSequences.addAll(expectedBySeq.keySet());
        allSequences.addAll(observedBySeq.keySet());

        int eventsMatched = 0;
        int eventsMissing = 0;
        int eventsUnexpected = 0;
        int severityMismatches = 0;
        int eventTypeMismatches = 0;
        int serviceMismatches = 0;
        boolean hasCriticalSeverityMismatch = false;

        List<ReplayVerificationDifference> differences = new ArrayList<>();

        for (int seq : allSequences) {
            ReplayScenarioEvent expected = expectedBySeq.get(seq);
            ReplayObservedEvent actual = observedBySeq.get(seq);

            if (expected != null && actual == null) {
                eventsMissing++;
                differences.add(new ReplayVerificationDifference(
                        verificationId,
                        seq,
                        VerificationDifferenceType.MISSING_EVENT,
                        expected.getEventId(),
                        null,
                        expected.getServiceName(),
                        null,
                        expected.getEventType().name(),
                        null,
                        expected.getSeverity().name(),
                        null,
                        "Expected event missing at sequence " + seq + ": eventId=" + expected.getEventId()
                ));
            } else if (expected == null && actual != null) {
                eventsUnexpected++;
                differences.add(new ReplayVerificationDifference(
                        verificationId,
                        seq,
                        VerificationDifferenceType.UNEXPECTED_EVENT,
                        null,
                        actual.eventId(),
                        null,
                        actual.serviceName(),
                        null,
                        actual.eventType() != null ? actual.eventType().name() : null,
                        null,
                        actual.severity() != null ? actual.severity().name() : null,
                        "Unexpected event observed at sequence " + seq + ": eventId=" + actual.eventId()
                ));
            } else if (expected != null) {
                boolean seqMatched = true;

                // Service mismatch check
                if (!Objects.equals(expected.getServiceName(), actual.serviceName())) {
                    serviceMismatches++;
                    seqMatched = false;
                    differences.add(new ReplayVerificationDifference(
                            verificationId,
                            seq,
                            VerificationDifferenceType.SERVICE_MISMATCH,
                            expected.getEventId(),
                            actual.eventId(),
                            expected.getServiceName(),
                            actual.serviceName(),
                            expected.getEventType().name(),
                            actual.eventType() != null ? actual.eventType().name() : null,
                            expected.getSeverity().name(),
                            actual.severity() != null ? actual.severity().name() : null,
                            "Service mismatch at sequence " + seq + ": expected '" + expected.getServiceName()
                                    + "', observed '" + actual.serviceName() + "'"
                    ));
                }

                // Event type mismatch check
                if (actual.eventType() == null || expected.getEventType() != actual.eventType()) {
                    eventTypeMismatches++;
                    seqMatched = false;
                    differences.add(new ReplayVerificationDifference(
                            verificationId,
                            seq,
                            VerificationDifferenceType.EVENT_TYPE_MISMATCH,
                            expected.getEventId(),
                            actual.eventId(),
                            expected.getServiceName(),
                            actual.serviceName(),
                            expected.getEventType().name(),
                            actual.eventType() != null ? actual.eventType().name() : null,
                            expected.getSeverity().name(),
                            actual.severity() != null ? actual.severity().name() : null,
                            "Event type mismatch at sequence " + seq + ": expected '" + expected.getEventType()
                                    + "', observed '" + actual.eventType() + "'"
                    ));
                }

                // Severity mismatch check
                if (actual.severity() == null || expected.getSeverity() != actual.severity()) {
                    severityMismatches++;
                    seqMatched = false;

                    boolean expectedIsFailure = isFailureSeverity(expected.getSeverity());
                    boolean actualIsFailure = isFailureSeverity(actual.severity());
                    if (expectedIsFailure != actualIsFailure) {
                        hasCriticalSeverityMismatch = true;
                    }

                    differences.add(new ReplayVerificationDifference(
                            verificationId,
                            seq,
                            VerificationDifferenceType.SEVERITY_MISMATCH,
                            expected.getEventId(),
                            actual.eventId(),
                            expected.getServiceName(),
                            actual.serviceName(),
                            expected.getEventType().name(),
                            actual.eventType() != null ? actual.eventType().name() : null,
                            expected.getSeverity().name(),
                            actual.severity() != null ? actual.severity().name() : null,
                            "Severity mismatch at sequence " + seq + ": expected '" + expected.getSeverity()
                                    + "', observed '" + actual.severity() + "'"
                    ));
                }

                // Event order mismatch check: if eventId differs, check if observed belongs to a different sequence
                if (actual.eventId() != null && !Objects.equals(expected.getEventId(), actual.eventId())) {
                    boolean existsElsewhere = expectedBySeq.values().stream()
                            .anyMatch(e -> Objects.equals(e.getEventId(), actual.eventId()));
                    if (existsElsewhere) {
                        seqMatched = false;
                        differences.add(new ReplayVerificationDifference(
                                verificationId,
                                seq,
                                VerificationDifferenceType.EVENT_ORDER_MISMATCH,
                                expected.getEventId(),
                                actual.eventId(),
                                expected.getServiceName(),
                                actual.serviceName(),
                                expected.getEventType().name(),
                                actual.eventType() != null ? actual.eventType().name() : null,
                                expected.getSeverity().name(),
                                actual.severity() != null ? actual.severity().name() : null,
                                "Event order mismatch at sequence " + seq + ": observed event '" + actual.eventId()
                                        + "' belongs to a different sequence"
                        ));
                    }
                }

                if (seqMatched) {
                    eventsMatched++;
                }
            }
        }

        // Calculate failure counts
        int originalFailureCount = scenario.getFailureCount();
        int replayedFailureCount = (int) safeObserved.stream().filter(this::isObservedFailure).count();

        // Determine verification status according to strict architectural rules
        boolean isPassed = (eventsMissing == 0)
                && (eventsUnexpected == 0)
                && (severityMismatches == 0)
                && (eventTypeMismatches == 0)
                && (serviceMismatches == 0)
                && (originalFailureCount == replayedFailureCount);

        boolean isFailed = (originalFailureCount != replayedFailureCount)
                || (eventsMissing > 0)
                || (serviceMismatches > 0)
                || (eventTypeMismatches > 0)
                || hasCriticalSeverityMismatch;

        ReplayVerificationStatus status;
        String resultMessage;

        if (isPassed) {
            status = ReplayVerificationStatus.PASSED;
            resultMessage = "Replay verified successfully: all " + eventsMatched + " events matched original failure characteristics";
        } else if (isFailed) {
            status = ReplayVerificationStatus.FAILED;
            resultMessage = "Replay verification failed: " + differences.size() + " difference(s) detected altering failure reproduction";
        } else {
            status = ReplayVerificationStatus.PARTIAL;
            resultMessage = "Replay partially verified: failure characteristics reproduced with " + differences.size() + " non-critical difference(s)";
        }

        ReplayVerification verification = new ReplayVerification(
                verificationId,
                cleanScenarioId,
                status,
                scenario.getEventCount(),
                safeObserved.size(),
                originalFailureCount,
                replayedFailureCount,
                scenario.getDurationMs(),
                replayedDurationMs,
                eventsMatched,
                eventsMissing,
                eventsUnexpected,
                severityMismatches,
                eventTypeMismatches,
                serviceMismatches,
                resultMessage
        );

        ReplayVerification savedVerification = verificationRepository.saveAndFlush(verification);
        List<ReplayVerificationDifference> savedDifferences = differences.isEmpty()
                ? Collections.emptyList()
                : differenceRepository.saveAllAndFlush(differences);

        log.info("Persisted replay verification [{}] for scenario [{}] with status [{}] and {} differences",
                verificationId, cleanScenarioId, status, savedDifferences.size());

        return new ReplayVerificationResult(
                ReplayVerificationResponse.from(savedVerification, savedDifferences),
                true
        );
    }

    /**
     * Retrieves an existing verification by its deterministic verification identifier.
     *
     * @param verificationId deterministic verification identifier
     * @return full verification response with differences
     * @throws ResourceNotFoundException if verification is not found
     */
    @Transactional(readOnly = true)
    public ReplayVerificationResponse getVerification(String verificationId) {
        if (verificationId == null || verificationId.isBlank()) {
            throw new ResourceNotFoundException("Verification not found with id: " + verificationId);
        }

        String cleanVerificationId = verificationId.trim();
        ReplayVerification verification = verificationRepository.findByVerificationId(cleanVerificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Verification not found with id: " + cleanVerificationId));

        List<ReplayVerificationDifference> differences = differenceRepository
                .findAllByVerificationIdOrderBySequenceNumberAsc(cleanVerificationId);

        return ReplayVerificationResponse.from(verification, differences);
    }

    /**
     * Retrieves all verification attempts for a specific replay scenario ordered by creation time descending.
     *
     * @param scenarioId deterministic scenario identifier
     * @return list of verifications for the scenario
     * @throws ResourceNotFoundException if scenario is not found
     */
    @Transactional(readOnly = true)
    public List<ReplayVerificationResponse> getVerificationsForScenario(String scenarioId) {
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new ResourceNotFoundException("Scenario not found with id: " + scenarioId);
        }

        String cleanScenarioId = scenarioId.trim();
        if (!scenarioRepository.existsByScenarioId(cleanScenarioId)) {
            throw new ResourceNotFoundException("Scenario not found with id: " + cleanScenarioId);
        }

        List<ReplayVerification> verifications = verificationRepository
                .findAllByScenarioIdOrderByCreatedAtDesc(cleanScenarioId);

        return verifications.stream()
                .map(v -> {
                    List<ReplayVerificationDifference> diffs = differenceRepository
                            .findAllByVerificationIdOrderBySequenceNumberAsc(v.getVerificationId());
                    return ReplayVerificationResponse.from(v, diffs);
                })
                .toList();
    }

    private boolean isFailureSeverity(Severity severity) {
        return severity == Severity.ERROR || severity == Severity.FATAL;
    }

    private boolean isObservedFailure(ReplayObservedEvent event) {
        return event.severity() == Severity.ERROR
                || event.severity() == Severity.FATAL
                || event.eventType() == EventType.ERROR;
    }

    /**
     * Generates a deterministic SHA-256 verification identifier based on scenarioId, expected events,
     * observed events, and comparison attributes.
     */
    private String generateDeterministicVerificationId(
            String scenarioId,
            List<ReplayScenarioEvent> expectedEvents,
            List<ReplayObservedEvent> observedEvents,
            long replayedDurationMs
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            sb.append(scenarioId).append(";duration=").append(replayedDurationMs).append(";exp:");
            for (ReplayScenarioEvent exp : expectedEvents) {
                sb.append(exp.getSequenceNumber())
                        .append(",")
                        .append(exp.getEventId())
                        .append(",")
                        .append(exp.getServiceName())
                        .append(",")
                        .append(exp.getEventType())
                        .append(",")
                        .append(exp.getSeverity())
                        .append(",")
                        .append(exp.getOffsetMs())
                        .append(";");
            }
            sb.append("obs:");
            for (ReplayObservedEvent obs : observedEvents) {
                sb.append(obs.sequenceNumber())
                        .append(",")
                        .append(obs.eventId())
                        .append(",")
                        .append(obs.serviceName())
                        .append(",")
                        .append(obs.eventType())
                        .append(",")
                        .append(obs.severity())
                        .append(",")
                        .append(obs.offsetMs())
                        .append(";");
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
            return "verify-" + hexString;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Result of scenario verification containing the response DTO and a flag indicating if it was newly created.
     */
    public record ReplayVerificationResult(
            ReplayVerificationResponse verification,
            boolean newlyCreated
    ) {
    }
}

