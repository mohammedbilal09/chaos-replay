package com.chaosreplay.api.dto;

import com.chaosreplay.domain.ReplayVerification;
import com.chaosreplay.domain.ReplayVerificationDifference;
import com.chaosreplay.domain.ReplayVerificationStatus;

import java.time.Instant;
import java.util.List;

/**
 * Immutable DTO representing a complete failure replay verification result.
 */
public record ReplayVerificationResponse(
        String verificationId,
        String scenarioId,
        ReplayVerificationStatus status,
        int originalEventCount,
        int replayedEventCount,
        int originalFailureCount,
        int replayedFailureCount,
        long originalDurationMs,
        long replayedDurationMs,
        int eventsMatched,
        int eventsMissing,
        int eventsUnexpected,
        int severityMismatches,
        int eventTypeMismatches,
        int serviceMismatches,
        String resultMessage,
        Instant createdAt,
        List<ReplayVerificationDifferenceResponse> differences
) {
    public static ReplayVerificationResponse from(
            ReplayVerification verification,
            List<ReplayVerificationDifference> diffEntities
    ) {
        List<ReplayVerificationDifferenceResponse> diffResponses = diffEntities != null
                ? diffEntities.stream().map(ReplayVerificationDifferenceResponse::from).toList()
                : List.of();

        return new ReplayVerificationResponse(
                verification.getVerificationId(),
                verification.getScenarioId(),
                verification.getStatus(),
                verification.getOriginalEventCount(),
                verification.getReplayedEventCount(),
                verification.getOriginalFailureCount(),
                verification.getReplayedFailureCount(),
                verification.getOriginalDurationMs(),
                verification.getReplayedDurationMs(),
                verification.getEventsMatched(),
                verification.getEventsMissing(),
                verification.getEventsUnexpected(),
                verification.getSeverityMismatches(),
                verification.getEventTypeMismatches(),
                verification.getServiceMismatches(),
                verification.getResultMessage(),
                verification.getCreatedAt(),
                diffResponses
        );
    }
}

