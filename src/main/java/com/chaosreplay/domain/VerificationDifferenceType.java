package com.chaosreplay.domain;

/**
 * Types of differences identifiable during failure replay verification.
 */
public enum VerificationDifferenceType {
    MISSING_EVENT,
    UNEXPECTED_EVENT,
    EVENT_TYPE_MISMATCH,
    SEVERITY_MISMATCH,
    SERVICE_MISMATCH,
    EVENT_ORDER_MISMATCH
}

