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

import java.util.Objects;

/**
 * Persistence entity representing concrete empirical evidence associated with a failure candidate.
 */
@Entity
@Table(
        name = "failure_analysis_evidence",
        indexes = {
                @Index(name = "idx_failure_analysis_evidence_analysis_candidate", columnList = "analysis_id, candidate_rank")
        }
)
public class FailureAnalysisEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "analysis_id", nullable = false, length = 64)
    private String analysisId;

    @Column(name = "candidate_rank", nullable = false)
    private int candidateRank;

    @Column(name = "sequence_number")
    private Integer sequenceNumber;

    @Column(name = "event_id", length = 64)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 64)
    private FailureEvidenceType evidenceType;

    @Column(name = "evidence_value", columnDefinition = "TEXT")
    private String evidenceValue;

    /**
     * Default constructor required by JPA.
     */
    protected FailureAnalysisEvidence() {
    }

    public FailureAnalysisEvidence(
            String analysisId,
            int candidateRank,
            Integer sequenceNumber,
            String eventId,
            FailureEvidenceType evidenceType,
            String evidenceValue
    ) {
        this.analysisId = analysisId;
        this.candidateRank = candidateRank;
        this.sequenceNumber = sequenceNumber;
        this.eventId = eventId;
        this.evidenceType = evidenceType;
        this.evidenceValue = evidenceValue;
    }

    public Long getId() {
        return id;
    }

    public String getAnalysisId() {
        return analysisId;
    }

    public int getCandidateRank() {
        return candidateRank;
    }

    public Integer getSequenceNumber() {
        return sequenceNumber;
    }

    public String getEventId() {
        return eventId;
    }

    public FailureEvidenceType getEvidenceType() {
        return evidenceType;
    }

    public String getEvidenceValue() {
        return evidenceValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FailureAnalysisEvidence that = (FailureAnalysisEvidence) o;
        return candidateRank == that.candidateRank &&
                Objects.equals(analysisId, that.analysisId) &&
                Objects.equals(eventId, that.eventId) &&
                evidenceType == that.evidenceType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(analysisId, candidateRank, eventId, evidenceType);
    }

    @Override
    public String toString() {
        return "FailureAnalysisEvidence{" +
                "id=" + id +
                ", analysisId='" + analysisId + '\'' +
                ", candidateRank=" + candidateRank +
                ", eventId='" + eventId + '\'' +
                ", evidenceType=" + evidenceType +
                '}';
    }
}

