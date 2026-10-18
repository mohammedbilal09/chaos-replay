package com.chaosreplay.domain;

/**
 * Deterministic category classifications for incident timeline entries.
 */
public enum IncidentEntryCategory {
    NORMAL_EVENT,
    ERROR_EVENT,
    FATAL_EVENT,
    DATABASE_FAILURE,
    EXTERNAL_CALL_FAILURE,
    TIMEOUT_SIGNAL,
    DOWNSTREAM_ERROR,
    REPLAY_DIVERGENCE
}

