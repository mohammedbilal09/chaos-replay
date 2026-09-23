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
 * Persistence entity representing an individual behavioral difference observed during replay verification.
 */
@Entity
@Table(
        name = "replay_verification_differences",
        indexes = {
                @Index(name = "idx_replay_verification_diff_seq", columnList = "verification_id, sequence_number")
        }
)
public class ReplayVerificationDifference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "verification_id", nullable = false, length = 64)
    private String verificationId;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "difference_type", nullable = false, length = 32)
    private VerificationDifferenceType differenceType;

    @Column(name = "expected_event_id", length = 64)
    private String expectedEventId;

    @Column(name = "actual_event_id", length = 64)
    private String actualEventId;

    @Column(name = "expected_service_name", length = 100)
    private String expectedServiceName;

    @Column(name = "actual_service_name", length = 100)
    private String actualServiceName;

    @Column(name = "expected_event_type", length = 32)
    private String expectedEventType;

    @Column(name = "actual_event_type", length = 32)
    private String actualEventType;

    @Column(name = "expected_severity", length = 20)
    private String expectedSeverity;

    @Column(name = "actual_severity", length = 20)
    private String actualSeverity;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    /**
     * Default constructor required by JPA.
     */
    protected ReplayVerificationDifference() {
    }

    public ReplayVerificationDifference(
            String verificationId,
            int sequenceNumber,
            VerificationDifferenceType differenceType,
            String expectedEventId,
            String actualEventId,
            String expectedServiceName,
            String actualServiceName,
            String expectedEventType,
            String actualEventType,
            String expectedSeverity,
            String actualSeverity,
            String message
    ) {
        this.verificationId = verificationId;
        this.sequenceNumber = sequenceNumber;
        this.differenceType = differenceType;
        this.expectedEventId = expectedEventId;
        this.actualEventId = actualEventId;
        this.expectedServiceName = expectedServiceName;
        this.actualServiceName = actualServiceName;
        this.expectedEventType = expectedEventType;
        this.actualEventType = actualEventType;
        this.expectedSeverity = expectedSeverity;
        this.actualSeverity = actualSeverity;
        this.message = message;
    }

    public Long getId() {
        return id;
    }

    public String getVerificationId() {
        return verificationId;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public VerificationDifferenceType getDifferenceType() {
        return differenceType;
    }

    public String getExpectedEventId() {
        return expectedEventId;
    }

    public String getActualEventId() {
        return actualEventId;
    }

    public String getExpectedServiceName() {
        return expectedServiceName;
    }

    public String getActualServiceName() {
        return actualServiceName;
    }

    public String getExpectedEventType() {
        return expectedEventType;
    }

    public String getActualEventType() {
        return actualEventType;
    }

    public String getExpectedSeverity() {
        return expectedSeverity;
    }

    public String getActualSeverity() {
        return actualSeverity;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReplayVerificationDifference that = (ReplayVerificationDifference) o;
        return sequenceNumber == that.sequenceNumber &&
                Objects.equals(verificationId, that.verificationId) &&
                differenceType == that.differenceType &&
                Objects.equals(expectedEventId, that.expectedEventId) &&
                Objects.equals(actualEventId, that.actualEventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(verificationId, sequenceNumber, differenceType, expectedEventId, actualEventId);
    }

    @Override
    public String toString() {
        return "ReplayVerificationDifference{" +
                "id=" + id +
                ", verificationId='" + verificationId + '\'' +
                ", sequenceNumber=" + sequenceNumber +
                ", differenceType=" + differenceType +
                ", expectedEventId='" + expectedEventId + '\'' +
                ", actualEventId='" + actualEventId + '\'' +
                ", expectedServiceName='" + expectedServiceName + '\'' +
                ", actualServiceName='" + actualServiceName + '\'' +
                ", expectedEventType='" + expectedEventType + '\'' +
                ", actualEventType='" + actualEventType + '\'' +
                ", expectedSeverity='" + expectedSeverity + '\'' +
                ", actualSeverity='" + actualSeverity + '\'' +
                '}';
    }
}

