package com.chaosreplay.api.dto;

import com.chaosreplay.domain.FailureAnalysisConclusion;
import com.chaosreplay.domain.FailureAnalysisStatus;
import com.chaosreplay.domain.ReplayScenarioStatus;
import com.chaosreplay.domain.ReplayVerificationStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable DTO providing a consolidated incident investigation view aggregating
 * telemetry timeline, replay scenario, verification status, candidate causes, and explicit limitations.
 */
public record IncidentInvestigationResponse(
        String traceId,
        IncidentTimelineResponse timeline,
        boolean correlationAvailable,
        boolean scenarioAvailable,
        String scenarioId,
        boolean executionAvailable,
        ReplayScenarioStatus executionStatus,
        boolean verificationAvailable,
        String verificationId,
        ReplayVerificationStatus verificationStatus,
        String verificationSummary,
        boolean analysisAvailable,
        String analysisId,
        FailureAnalysisStatus analysisStatus,
        FailureAnalysisConclusion conclusion,
        BigDecimal confidenceScore,
        String primaryServiceName,
        String primaryEventId,
        List<FailureAnalysisCandidateResponse> candidates,
        List<String> evidenceSummary,
        List<String> limitations
) {
    public IncidentInvestigationResponse {
        candidates = candidates != null ? List.copyOf(candidates) : List.of();
        evidenceSummary = evidenceSummary != null ? List.copyOf(evidenceSummary) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}

