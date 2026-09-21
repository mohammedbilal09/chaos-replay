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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Persistence entity representing an individual distributed telemetry event.
 * <p>
 * Designed to preserve event lineage, temporal order, distributed tracing links,
 * and contextual payloads necessary for future incident reconstruction and replay phases.
 */
@Entity
@Table(
        name = "telemetry_events",
        indexes = {
                @Index(name = "idx_telemetry_events_timestamp", columnList = "timestamp DESC"),
                @Index(name = "idx_telemetry_events_service_timestamp", columnList = "service_name, timestamp DESC"),
                @Index(name = "idx_telemetry_events_trace_id", columnList = "trace_id"),
                @Index(name = "idx_telemetry_events_request_id", columnList = "request_id"),
                @Index(name = "idx_telemetry_events_type_severity", columnList = "event_type, severity")
        }
)
public class TelemetryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Default constructor required by JPA.
     */
    protected TelemetryEvent() {
    }

    public TelemetryEvent(
            String eventId,
            Instant timestamp,
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
        this.eventId = eventId;
        this.timestamp = timestamp;
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

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public Instant getTimestamp() {
        return timestamp;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TelemetryEvent that = (TelemetryEvent) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }

    @Override
    public String toString() {
        return "TelemetryEvent{" +
                "id=" + id +
                ", eventId='" + eventId + '\'' +
                ", timestamp=" + timestamp +
                ", serviceName='" + serviceName + '\'' +
                ", serviceInstance='" + serviceInstance + '\'' +
                ", eventType=" + eventType +
                ", severity=" + severity +
                ", traceId='" + traceId + '\'' +
                ", requestId='" + requestId + '\'' +
                ", operation='" + operation + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}

