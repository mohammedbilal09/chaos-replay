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
 * Persistence entity representing a deterministic replay scenario derived from telemetry.
 */
@Entity
@Table(
        name = "replay_scenarios",
        indexes = {
                @Index(name = "idx_replay_scenarios_source_trace_id", columnList = "source_trace_id")
        }
)
public class ReplayScenario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scenario_id", nullable = false, unique = true, length = 64)
    private String scenarioId;

    @Column(name = "source_trace_id", nullable = false, length = 64)
    private String sourceTraceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "event_count", nullable = false)
    private int eventCount;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ReplayScenarioStatus status;

    /**
     * Default constructor required by JPA.
     */
    protected ReplayScenario() {
    }

    public ReplayScenario(
            String scenarioId,
            String sourceTraceId,
            int eventCount,
            int failureCount,
            long durationMs,
            ReplayScenarioStatus status
    ) {
        this.scenarioId = scenarioId;
        this.sourceTraceId = sourceTraceId;
        this.eventCount = eventCount;
        this.failureCount = failureCount;
        this.durationMs = durationMs;
        this.status = status;
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

    public String getScenarioId() {
        return scenarioId;
    }

    public String getSourceTraceId() {
        return sourceTraceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getEventCount() {
        return eventCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public ReplayScenarioStatus getStatus() {
        return status;
    }

    public void setStatus(ReplayScenarioStatus status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReplayScenario that = (ReplayScenario) o;
        return Objects.equals(scenarioId, that.scenarioId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scenarioId);
    }

    @Override
    public String toString() {
        return "ReplayScenario{" +
                "id=" + id +
                ", scenarioId='" + scenarioId + '\'' +
                ", sourceTraceId='" + sourceTraceId + '\'' +
                ", createdAt=" + createdAt +
                ", eventCount=" + eventCount +
                ", failureCount=" + failureCount +
                ", durationMs=" + durationMs +
                ", status=" + status +
                '}';
    }
}

