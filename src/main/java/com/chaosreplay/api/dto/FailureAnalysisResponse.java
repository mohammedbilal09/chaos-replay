package com.chaosreplay.api.dto;

import com.chaosreplay.domain.FailureAnalysis;
import com.chaosreplay.domain.FailureAnalysisConclusion;
import com.chaosreplay.domain.FailureAnalysisStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Immutable DTO representing a complete, evidence-backed failure analysis result.
 */
public record FailureAnalysisResponse(
        String analysisId,
        String traceId,
        String scenarioId,
        String verificationId,
        FailureAnalysisStatus status,
        FailureAnalysisConclusion conclusion,
        BigDecimal confidenceScore,
        int eventCount,
        int failureCount,
        int candidateCount,
        String primaryServiceName,
        String primaryEventId,
        String summary,
        List<FailureAnalysisCandidateResponse> candidates,
        Instant createdAt
) {
    public static FailureAnalysisResponse from(
            FailureAnalysis analysis,
            List<FailureAnalysisCandidateResponse> candidates
    ) {
        return new FailureAnalysisResponse(
                analysis.getAnalysisId(),
                analysis.getTraceId(),
                analysis.getScenarioId(),
                analysis.getVerificationId(),
                analysis.getStatus(),
                analysis.getConclusion(),
                analysis.getConfidenceScore(),
                analysis.getEventCount(),
                analysis.getFailureCount(),
                analysis.getCandidateCount(),
                analysis.getPrimaryServiceName(),
                analysis.getPrimaryEventId(),
                analysis.getSummary(),
                candidates != null ? candidates : List.of(),
                analysis.getCreatedAt()
        );
    }
}

