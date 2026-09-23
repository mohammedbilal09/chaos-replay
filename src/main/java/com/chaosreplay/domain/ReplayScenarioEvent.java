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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.Objects;

/**
 * Persistence entity representing an individual ordered event snapshot within a replay scenario.
 */
@Entity
@Table(
        name = "replay_scenario_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_replay_scenario_events_scenario_seq",
                        columnNames = {"scenario_id", "sequence_number"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_replay_scenario_events_scenario_seq",
                        columnList = "scenario_id, sequence_number"
                )
        }
)
public class ReplayScenarioEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scenario_id", nullable = false, length = 64)
    private String scenarioId;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "offset_ms", nullable = false)
    private long offsetMs;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Column(name = "service_instance", length = 100)
    private String serviceInstance;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private Severity severity;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "operation", length = 255)
    private String operation;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    /**
     * Default constructor required by JPA.
     */
    protected ReplayScenarioEvent() {
    }

    public ReplayScenarioEvent(
            String scenarioId,
            String eventId,
            long offsetMs,
            int sequenceNumber,
            String serviceName,
            String serviceInstance,
            EventType eventType,
            Severity severity,
            String traceId,
            String requestId,
            String operation,
            String message,
            Map<String, Object> metadata
    ) {
        this.scenarioId = scenarioId;
        this.eventId = eventId;
        this.offsetMs = offsetMs;
        this.sequenceNumber = sequenceNumber;
        this.serviceName = serviceName;
        this.serviceInstance = serviceInstance;
        this.eventType = eventType;
        this.severity = severity;
        this.traceId = traceId;
        this.requestId = requestId;
        this.operation = operation;
        this.message = message;
        this.metadata = metadata;
    }

    public Long getId() {
        return id;
    }

    public String getScenarioId() {
        return scenarioId;
    }

    public String getEventId() {
        return eventId;
    }

    public long getOffsetMs() {
        return offsetMs;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getServiceInstance() {
        return serviceInstance;
    }

    public EventType getEventType() {
        return eventType;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getOperation() {
        return operation;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReplayScenarioEvent that = (ReplayScenarioEvent) o;
        return sequenceNumber == that.sequenceNumber && Objects.equals(scenarioId, that.scenarioId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scenarioId, sequenceNumber);
    }

    @Override
    public String toString() {
        return "ReplayScenarioEvent{" +
                "id=" + id +
                ", scenarioId='" + scenarioId + '\'' +
                ", eventId='" + eventId + '\'' +
                ", offsetMs=" + offsetMs +
                ", sequenceNumber=" + sequenceNumber +
                ", serviceName='" + serviceName + '\'' +
                ", eventType=" + eventType +
                ", severity=" + severity +
                '}';
    }
}

