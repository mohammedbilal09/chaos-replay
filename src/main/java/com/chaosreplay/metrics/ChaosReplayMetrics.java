package com.chaosreplay.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Centralized component managing ChaosReplay application metrics.
 * <p>
 * Enforces strict Micrometer conventions:
 * - Low-cardinality, bounded tags (never raw traceId, eventId, exception messages, or URLs).
 * - Timers for operation latencies.
 * - Counters for monotonically increasing event counts.
 */
@Component
public class ChaosReplayMetrics {

    private final MeterRegistry registry;

    public ChaosReplayMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public MeterRegistry getRegistry() {
        return registry;
    }

    // =========================================================================
    // Telemetry Ingestion Metrics
    // =========================================================================

    public void recordTelemetryIngested(String source, String status) {
        Counter.builder("chaosreplay.telemetry.ingested.total")
                .description("Total telemetry events ingested")
                .tag("source", source)
                .tag("status", status)
                .register(registry)
                .increment();
    }

    public void recordTelemetryIngestionDuration(String source, String status, long durationNanos) {
        Timer.builder("chaosreplay.telemetry.ingestion.duration")
                .description("Duration of telemetry event ingestion")
                .tag("source", source)
                .tag("status", status)
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordTelemetryDuplicate(String source) {
        Counter.builder("chaosreplay.telemetry.duplicate.total")
                .description("Total duplicate telemetry events rejected or absorbed")
                .tag("source", source)
                .register(registry)
                .increment();
    }

    public void recordTelemetryInvalid(String reason) {
        Counter.builder("chaosreplay.telemetry.invalid.total")
                .description("Total invalid or rejected telemetry events")
                .tag("reason", reason)
                .register(registry)
                .increment();
    }

    // =========================================================================
    // Kafka Ingestion Metrics
    // =========================================================================

    public void recordKafkaConsumed(String status) {
        Counter.builder("chaosreplay.kafka.consumed.total")
                .description("Total Kafka telemetry records processed")
                .tag("status", status)
                .register(registry)
                .increment();
    }

    public void recordKafkaConsumptionDuration(String status, long durationNanos) {
        Timer.builder("chaosreplay.kafka.consumption.duration")
                .description("Duration of Kafka message processing")
                .tag("status", status)
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordKafkaDlq(String reason) {
        Counter.builder("chaosreplay.kafka.dlq.total")
                .description("Total records routed to dead-letter queue")
                .tag("reason", reason)
                .register(registry)
                .increment();
    }

    // =========================================================================
    // Incident & Timeline Metrics
    // =========================================================================

    public void recordTimelineDuration(String status, long durationNanos) {
        Timer.builder("chaosreplay.incident.timeline.duration")
                .description("Duration of incident timeline reconstruction")
                .tag("status", status)
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordInvestigationDuration(String status, long durationNanos) {
        Timer.builder("chaosreplay.incident.investigation.duration")
                .description("Duration of incident investigation consolidation")
                .tag("status", status)
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    // =========================================================================
    // Replay & Analysis Metrics
    // =========================================================================

    public void recordReplayVerification(String outcome, long durationNanos) {
        Counter.builder("chaosreplay.replay.verification.total")
                .description("Total replay verifications executed")
                .tag("outcome", outcome)
                .register(registry)
                .increment();

        Timer.builder("chaosreplay.replay.verification.duration")
                .description("Duration of replay verification execution")
                .tag("outcome", outcome)
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordAnalysis(String status, long durationNanos) {
        Counter.builder("chaosreplay.analysis.total")
                .description("Total failure analyses executed")
                .tag("status", status)
                .register(registry)
                .increment();

        Timer.builder("chaosreplay.analysis.duration")
                .description("Duration of failure analysis execution")
                .tag("status", status)
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }
}

