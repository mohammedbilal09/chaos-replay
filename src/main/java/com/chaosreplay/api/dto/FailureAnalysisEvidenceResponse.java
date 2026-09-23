package com.chaosreplay.api.dto;

import com.chaosreplay.domain.FailureAnalysisEvidence;
import com.chaosreplay.domain.FailureEvidenceType;

/**
 * Immutable DTO representing an individual piece of supporting evidence for a failure candidate.
 */
public record FailureAnalysisEvidenceResponse(
        int sequenceNumber,
        String eventId,
        FailureEvidenceType evidenceType,
        String evidenceValue
) {
    public static FailureAnalysisEvidenceResponse fromEntity(FailureAnalysisEvidence entity) {
        return new FailureAnalysisEvidenceResponse(
                entity.getSequenceNumber() != null ? entity.getSequenceNumber() : 0,
                entity.getEventId(),
                entity.getEvidenceType(),
                entity.getEvidenceValue()
        );
    }
}

