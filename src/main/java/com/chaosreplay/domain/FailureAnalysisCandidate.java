package com.chaosreplay.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Persistence entity representing an individual ranked failure candidate within an analysis.
 */
@Entity
@Table(
        name = "failure_analysis_candidates",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_failure_analysis_candidates_rank",
                        columnNames = {"analysis_id", "rank"}
                )
        },
        indexes = {
                @Index(name = "idx_failure_analysis_candidates_analysis_id", columnList = "analysis_id"),
                @Index(name = "idx_failure_analysis_candidates_event_id", columnList = "event_id"),
                @Index(name = "idx_failure_analysis_candidates_service_name", columnList = "service_name")
        }
)
public class FailureAnalysisCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "analysis_id", nullable = false, length = 64)
    private String analysisId;

    @Column(name = "rank", nullable = false)
    private int rank;

    @Column(name = "service_name", length = 100)
    private String serviceName;

    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(name = "event_type", length = 32)
    private String eventType;

    @Column(name = "severity", length = 20)
    private String severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "candidate_type", nullable = false, length = 64)
    private FailureCandidateType candidateType;

    @Column(name = "confidence_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "first_observed_at")
    private Instant firstObservedAt;

    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;

    /**
     * Default constructor required by JPA.
     */
    protected FailureAnalysisCandidate() {
    }

    public FailureAnalysisCandidate(
            String analysisId,
            int rank,
            String serviceName,
            String eventId,
            String eventType,
            String severity,
            FailureCandidateType candidateType,
            BigDecimal confidenceScore,
            Instant firstObservedAt,
            String description
    ) {
        this.analysisId = analysisId;
        this.rank = rank;
        this.serviceName = serviceName;
        this.eventId = eventId;
        this.eventType = eventType;
        this.severity = severity;
        this.candidateType = candidateType;
        this.confidenceScore = confidenceScore;
        this.firstObservedAt = firstObservedAt;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public String getAnalysisId() {
        return analysisId;
    }

    public int getRank() {
        return rank;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getSeverity() {
        return severity;
    }

    public FailureCandidateType getCandidateType() {
        return candidateType;
    }

    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }

    public Instant getFirstObservedAt() {
        return firstObservedAt;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FailureAnalysisCandidate that = (FailureAnalysisCandidate) o;
        return rank == that.rank && Objects.equals(analysisId, that.analysisId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(analysisId, rank);
    }

    @Override
    public String toString() {
        return "FailureAnalysisCandidate{" +
                "id=" + id +
                ", analysisId='" + analysisId + '\'' +
                ", rank=" + rank +
                ", serviceName='" + serviceName + '\'' +
                ", eventId='" + eventId + '\'' +
                ", candidateType=" + candidateType +
                ", confidenceScore=" + confidenceScore +
                '}';
    }
}

