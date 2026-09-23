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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Persistence entity representing an immutable, evidence-backed failure analysis record.
 */
@Entity
@Table(
        name = "failure_analyses",
        indexes = {
                @Index(name = "idx_failure_analyses_trace_id", columnList = "trace_id"),
                @Index(name = "idx_failure_analyses_scenario_id", columnList = "scenario_id"),
                @Index(name = "idx_failure_analyses_verification_id", columnList = "verification_id"),
                @Index(name = "idx_failure_analyses_created_at", columnList = "created_at DESC")
        }
)
public class FailureAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "analysis_id", nullable = false, unique = true, length = 64)
    private String analysisId;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "scenario_id", length = 64)
    private String scenarioId;

    @Column(name = "verification_id", length = 64)
    private String verificationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private FailureAnalysisStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "conclusion", nullable = false, length = 64)
    private FailureAnalysisConclusion conclusion;

    @Column(name = "confidence_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "event_count", nullable = false)
    private int eventCount;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "candidate_count", nullable = false)
    private int candidateCount;

    @Column(name = "primary_service_name", length = 100)
    private String primaryServiceName;

    @Column(name = "primary_event_id", length = 64)
    private String primaryEventId;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Default constructor required by JPA.
     */
    protected FailureAnalysis() {
    }

    public FailureAnalysis(
            String analysisId,
            String traceId,
            String scenarioId,
            String verificationId,
            FailureAnalysisStatus status,
            FailureAnalysisConclusion conclusion,
            BigDecimal confidenceScore,
            int eventCount,
            int failureCount,
            int candidateCount,
            String primaryServiceName,
            String primaryEventId,
            String summary
    ) {
        this.analysisId = analysisId;
        this.traceId = traceId;
        this.scenarioId = scenarioId;
        this.verificationId = verificationId;
        this.status = status;
        this.conclusion = conclusion;
        this.confidenceScore = confidenceScore;
        this.eventCount = eventCount;
        this.failureCount = failureCount;
        this.candidateCount = candidateCount;
        this.primaryServiceName = primaryServiceName;
        this.primaryEventId = primaryEventId;
        this.summary = summary;
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

    public String getAnalysisId() {
        return analysisId;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getScenarioId() {
        return scenarioId;
    }

    public String getVerificationId() {
        return verificationId;
    }

    public FailureAnalysisStatus getStatus() {
        return status;
    }

    public FailureAnalysisConclusion getConclusion() {
        return conclusion;
    }

    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }

    public int getEventCount() {
        return eventCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public int getCandidateCount() {
        return candidateCount;
    }

    public String getPrimaryServiceName() {
        return primaryServiceName;
    }

    public String getPrimaryEventId() {
        return primaryEventId;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FailureAnalysis that = (FailureAnalysis) o;
        return Objects.equals(analysisId, that.analysisId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(analysisId);
    }

    @Override
    public String toString() {
        return "FailureAnalysis{" +
                "id=" + id +
                ", analysisId='" + analysisId + '\'' +
                ", traceId='" + traceId + '\'' +
                ", status=" + status +
                ", conclusion=" + conclusion +
                ", confidenceScore=" + confidenceScore +
                ", failureCount=" + failureCount +
                ", candidateCount=" + candidateCount +
                ", primaryServiceName='" + primaryServiceName + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}

