package com.chaosreplay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * Persistence entity representing an immutable failure replay verification record.
 */
@Entity
@Table(
        name = "replay_verifications",
        indexes = {
                @Index(name = "idx_replay_verifications_scenario_id", columnList = "scenario_id"),
                @Index(name = "idx_replay_verifications_created_at", columnList = "created_at DESC")
        }
)
public class ReplayVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "verification_id", nullable = false, unique = true, length = 64)
    private String verificationId;

    @Column(name = "scenario_id", nullable = false, length = 64)
    private String scenarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ReplayVerificationStatus status;

    @Column(name = "original_event_count", nullable = false)
    private int originalEventCount;

    @Column(name = "replayed_event_count", nullable = false)
    private int replayedEventCount;

    @Column(name = "original_failure_count", nullable = false)
    private int originalFailureCount;

    @Column(name = "replayed_failure_count", nullable = false)
    private int replayedFailureCount;

    @Column(name = "original_duration_ms", nullable = false)
    private long originalDurationMs;

    @Column(name = "replayed_duration_ms", nullable = false)
    private long replayedDurationMs;

    @Column(name = "events_matched", nullable = false)
    private int eventsMatched;

    @Column(name = "events_missing", nullable = false)
    private int eventsMissing;

    @Column(name = "events_unexpected", nullable = false)
    private int eventsUnexpected;

    @Column(name = "severity_mismatches", nullable = false)
    private int severityMismatches;

    @Column(name = "event_type_mismatches", nullable = false)
    private int eventTypeMismatches;

    @Column(name = "service_mismatches", nullable = false)
    private int serviceMismatches;

    @Column(name = "result_message", columnDefinition = "TEXT")
    private String resultMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Default constructor required by JPA.
     */
    protected ReplayVerification() {
    }

    public ReplayVerification(
            String verificationId,
            String scenarioId,
            ReplayVerificationStatus status,
            int originalEventCount,
            int replayedEventCount,
            int originalFailureCount,
            int replayedFailureCount,
            long originalDurationMs,
            long replayedDurationMs,
            int eventsMatched,
            int eventsMissing,
            int eventsUnexpected,
            int severityMismatches,
            int eventTypeMismatches,
            int serviceMismatches,
            String resultMessage
    ) {
        this.verificationId = verificationId;
        this.scenarioId = scenarioId;
        this.status = status;
        this.originalEventCount = originalEventCount;
        this.replayedEventCount = replayedEventCount;
        this.originalFailureCount = originalFailureCount;
        this.replayedFailureCount = replayedFailureCount;
        this.originalDurationMs = originalDurationMs;
        this.replayedDurationMs = replayedDurationMs;
        this.eventsMatched = eventsMatched;
        this.eventsMissing = eventsMissing;
        this.eventsUnexpected = eventsUnexpected;
        this.severityMismatches = severityMismatches;
        this.eventTypeMismatches = eventTypeMismatches;
        this.serviceMismatches = serviceMismatches;
        this.resultMessage = resultMessage;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getVerificationId() {
        return verificationId;
    }

    public String getScenarioId() {
        return scenarioId;
    }

    public ReplayVerificationStatus getStatus() {
        return status;
    }

    public int getOriginalEventCount() {
        return originalEventCount;
    }

    public int getReplayedEventCount() {
        return replayedEventCount;
    }

    public int getOriginalFailureCount() {
        return originalFailureCount;
    }

    public int getReplayedFailureCount() {
        return replayedFailureCount;
    }

    public long getOriginalDurationMs() {
        return originalDurationMs;
    }

    public long getReplayedDurationMs() {
        return replayedDurationMs;
    }

    public int getEventsMatched() {
        return eventsMatched;
    }

    public int getEventsMissing() {
        return eventsMissing;
    }

    public int getEventsUnexpected() {
        return eventsUnexpected;
    }

    public int getSeverityMismatches() {
        return severityMismatches;
    }

    public int getEventTypeMismatches() {
        return eventTypeMismatches;
    }

    public int getServiceMismatches() {
        return serviceMismatches;
    }

    public String getResultMessage() {
        return resultMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReplayVerification that = (ReplayVerification) o;
        return Objects.equals(verificationId, that.verificationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(verificationId);
    }

    @Override
    public String toString() {
        return "ReplayVerification{" +
                "id=" + id +
                ", verificationId='" + verificationId + '\'' +
                ", scenarioId='" + scenarioId + '\'' +
                ", status=" + status +
                ", originalEventCount=" + originalEventCount +
                ", replayedEventCount=" + replayedEventCount +
                ", originalFailureCount=" + originalFailureCount +
                ", replayedFailureCount=" + replayedFailureCount +
                ", eventsMatched=" + eventsMatched +
                ", eventsMissing=" + eventsMissing +
                ", eventsUnexpected=" + eventsUnexpected +
                ", severityMismatches=" + severityMismatches +
                ", eventTypeMismatches=" + eventTypeMismatches +
                ", serviceMismatches=" + serviceMismatches +
                ", createdAt=" + createdAt +
                '}';
    }
}

