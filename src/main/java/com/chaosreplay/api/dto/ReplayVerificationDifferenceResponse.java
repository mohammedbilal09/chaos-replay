package com.chaosreplay.api.dto;

import com.chaosreplay.domain.ReplayVerificationDifference;
import com.chaosreplay.domain.VerificationDifferenceType;

/**
 * Immutable DTO representing an individual difference detected during failure replay verification.
 */
public record ReplayVerificationDifferenceResponse(
        int sequenceNumber,
        VerificationDifferenceType differenceType,
        String expectedEventId,
        String actualEventId,
        String expectedServiceName,
        String actualServiceName,
        String expectedEventType,
        String actualEventType,
        String expectedSeverity,
        String actualSeverity,
        String message
) {
    public static ReplayVerificationDifferenceResponse from(ReplayVerificationDifference diff) {
        return new ReplayVerificationDifferenceResponse(
                diff.getSequenceNumber(),
                diff.getDifferenceType(),
                diff.getExpectedEventId(),
                diff.getActualEventId(),
                diff.getExpectedServiceName(),
                diff.getActualServiceName(),
                diff.getExpectedEventType(),
                diff.getActualEventType(),
                diff.getExpectedSeverity(),
                diff.getActualSeverity(),
                diff.getMessage()
        );
    }
}

