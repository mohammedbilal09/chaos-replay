package com.chaosreplay.domain;

/**
 * Controlled taxonomy of failure candidate categories supported by ChaosReplay.
 */
public enum FailureCandidateType {
    DATABASE_FAILURE,
    EXTERNAL_DEPENDENCY_FAILURE,
    APPLICATION_ERROR,
    TIMEOUT,
    SERVICE_FAILURE,
    DOWNSTREAM_FAILURE,
    UNKNOWN_FAILURE
}

