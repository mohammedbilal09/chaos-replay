package com.chaosreplay.domain;

/**
 * Categorical conclusion derived from deterministic failure evidence evaluation.
 */
public enum FailureAnalysisConclusion {
    ROOT_CAUSE_CANDIDATE,
    MULTIPLE_POSSIBLE_CAUSES,
    REPLAY_DIVERGENCE,
    NO_FAILURE_OBSERVED,
    INSUFFICIENT_EVIDENCE
}

