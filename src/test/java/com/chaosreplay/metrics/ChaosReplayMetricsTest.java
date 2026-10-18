package com.chaosreplay.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ChaosReplayMetricsTest {

    private MeterRegistry registry;
    private ChaosReplayMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new ChaosReplayMetrics(registry);
    }

    @Test
    @DisplayName("Telemetry metrics record ingested count, duration, duplicates, and invalid events with low-cardinality tags")
    void telemetryMetrics_recordCorrectly() {
        metrics.recordTelemetryIngested("rest", "success");
        metrics.recordTelemetryIngested("kafka", "success");
        metrics.recordTelemetryIngestionDuration("rest", "success", 15_000_000L);
        metrics.recordTelemetryDuplicate("rest");
        metrics.recordTelemetryDuplicate("kafka");
        metrics.recordTelemetryInvalid("validation");
        metrics.recordTelemetryInvalid("malformed");

        Counter restIngested = registry.find("chaosreplay.telemetry.ingested.total")
                .tag("source", "rest")
                .tag("status", "success")
                .counter();
        assertThat(restIngested).isNotNull();
        assertThat(restIngested.count()).isEqualTo(1.0);

        Counter kafkaIngested = registry.find("chaosreplay.telemetry.ingested.total")
                .tag("source", "kafka")
                .tag("status", "success")
                .counter();
        assertThat(kafkaIngested).isNotNull();
        assertThat(kafkaIngested.count()).isEqualTo(1.0);

        Timer timer = registry.find("chaosreplay.telemetry.ingestion.duration")
                .tag("source", "rest")
                .tag("status", "success")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(14.0);

        Counter restDup = registry.find("chaosreplay.telemetry.duplicate.total")
                .tag("source", "rest")
                .counter();
        assertThat(restDup).isNotNull();
        assertThat(restDup.count()).isEqualTo(1.0);

        Counter invalidVal = registry.find("chaosreplay.telemetry.invalid.total")
                .tag("reason", "validation")
                .counter();
        assertThat(invalidVal).isNotNull();
        assertThat(invalidVal.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Kafka metrics record consumption counts, duration, and DLQ counters")
    void kafkaMetrics_recordCorrectly() {
        metrics.recordKafkaConsumed("success");
        metrics.recordKafkaConsumed("duplicate");
        metrics.recordKafkaConsumed("failure");
        metrics.recordKafkaConsumptionDuration("success", 25_000_000L);
        metrics.recordKafkaDlq("invalid_message");

        Counter successCounter = registry.find("chaosreplay.kafka.consumed.total")
                .tag("status", "success")
                .counter();
        assertThat(successCounter).isNotNull();
        assertThat(successCounter.count()).isEqualTo(1.0);

        Counter dlqCounter = registry.find("chaosreplay.kafka.dlq.total")
                .tag("reason", "invalid_message")
                .counter();
        assertThat(dlqCounter).isNotNull();
        assertThat(dlqCounter.count()).isEqualTo(1.0);

        Timer durationTimer = registry.find("chaosreplay.kafka.consumption.duration")
                .tag("status", "success")
                .timer();
        assertThat(durationTimer).isNotNull();
        assertThat(durationTimer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Incident, replay, and analysis metrics record durations and outcomes")
    void incidentReplayAnalysisMetrics_recordCorrectly() {
        metrics.recordTimelineDuration("success", 30_000_000L);
        metrics.recordInvestigationDuration("success", 45_000_000L);
        metrics.recordReplayVerification("PASSED", 50_000_000L);
        metrics.recordAnalysis("COMPLETED", 60_000_000L);

        Timer timelineTimer = registry.find("chaosreplay.incident.timeline.duration")
                .tag("status", "success")
                .timer();
        assertThat(timelineTimer).isNotNull();
        assertThat(timelineTimer.count()).isEqualTo(1);

        Timer investigationTimer = registry.find("chaosreplay.incident.investigation.duration")
                .tag("status", "success")
                .timer();
        assertThat(investigationTimer).isNotNull();
        assertThat(investigationTimer.count()).isEqualTo(1);

        Counter verifyCounter = registry.find("chaosreplay.replay.verification.total")
                .tag("outcome", "PASSED")
                .counter();
        assertThat(verifyCounter).isNotNull();
        assertThat(verifyCounter.count()).isEqualTo(1.0);

        Timer verifyTimer = registry.find("chaosreplay.replay.verification.duration")
                .tag("outcome", "PASSED")
                .timer();
        assertThat(verifyTimer).isNotNull();
        assertThat(verifyTimer.count()).isEqualTo(1);

        Counter analysisCounter = registry.find("chaosreplay.analysis.total")
                .tag("status", "COMPLETED")
                .counter();
        assertThat(analysisCounter).isNotNull();
        assertThat(analysisCounter.count()).isEqualTo(1.0);

        Timer analysisTimer = registry.find("chaosreplay.analysis.duration")
                .tag("status", "COMPLETED")
                .timer();
        assertThat(analysisTimer).isNotNull();
        assertThat(analysisTimer.count()).isEqualTo(1);
    }
}

