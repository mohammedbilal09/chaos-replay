package com.chaosreplay.domain;

/**
 * Types of empirical evidence attached to failure candidates.
 */
public enum FailureEvidenceType {
    FIRST_FAILURE,
    ERROR_EVENT,
    FATAL_EVENT,
    DOWNSTREAM_ERROR,
    TIMEOUT_SIGNAL,
    DATABASE_ERROR,
    EXTERNAL_CALL_ERROR,
    REPLAY_MISMATCH,
    SERVICE_PROPAGATION,
    TEMPORAL_PRECEDENCE
}

