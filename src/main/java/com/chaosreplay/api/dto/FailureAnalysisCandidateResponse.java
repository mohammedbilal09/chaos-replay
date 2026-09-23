package com.chaosreplay.api.dto;

import com.chaosreplay.domain.FailureAnalysisCandidate;
import com.chaosreplay.domain.FailureCandidateType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Immutable DTO representing a ranked failure cause candidate with attached evidence.
 */
public record FailureAnalysisCandidateResponse(
        int rank,
        String serviceName,
        String eventId,
        String eventType,
        String severity,
        FailureCandidateType candidateType,
        BigDecimal confidenceScore,
        Instant firstObservedAt,
        String description,
        List<FailureAnalysisEvidenceResponse> evidence
) {
    public static FailureAnalysisCandidateResponse from(
            FailureAnalysisCandidate candidate,
            List<FailureAnalysisEvidenceResponse> evidence
    ) {
        return new FailureAnalysisCandidateResponse(
                candidate.getRank(),
                candidate.getServiceName(),
                candidate.getEventId(),
                candidate.getEventType(),
                candidate.getSeverity(),
                candidate.getCandidateType(),
                candidate.getConfidenceScore(),
                candidate.getFirstObservedAt(),
                candidate.getDescription(),
                evidence != null ? evidence : List.of()
        );
    }
}

