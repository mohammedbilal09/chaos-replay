package com.chaosreplay.integration;

import com.chaosreplay.api.dto.CreateTelemetryEventRequest;
import com.chaosreplay.api.dto.IncidentInvestigationResponse;
import com.chaosreplay.api.dto.IncidentTimelineResponse;
import com.chaosreplay.api.dto.TraceCorrelationResponse;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.IncidentEntryCategory;
import com.chaosreplay.domain.Severity;
import com.chaosreplay.domain.TelemetryEvent;
import com.chaosreplay.exception.ResourceNotFoundException;
import com.chaosreplay.kafka.TelemetryKafkaProducer;
import com.chaosreplay.kafka.TelemetryMessage;
import com.chaosreplay.repository.TelemetryEventRepository;
import com.chaosreplay.service.IncidentInvestigationService;
import com.chaosreplay.service.IncidentTimelineService;
import com.chaosreplay.service.TelemetryService;
import com.chaosreplay.service.TraceCorrelationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Real Testcontainers integration test validating asynchronous, event-driven telemetry
 * ingestion with Apache Kafka and PostgreSQL.
 * <p>
 * Verifies:
 * 1. Valid Kafka message consumption and persistence into PostgreSQL.
 * 2. Visibility through existing trace correlation APIs.
 * 3. Duplicate message delivery absorption (idempotency, zero duplicate rows).
 * 4. Multiple events for the same trace correlated chronologically.
 * 5. Dead-letter queue (DLQ) routing on malformed payloads without crashing the consumer.
 * 6. Non-retryable validation failures routed directly to DLQ with metadata headers.
 * 7. Seamless coexistence of REST-ingested and Kafka-ingested telemetry.
 * 8. Full Step 7 Incident Timeline and Consolidated Investigation compatibility.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "chaosreplay.kafka.enabled=true",
        "chaosreplay.kafka.consumer-group=chaosreplay-integration-group",
        "chaosreplay.kafka.telemetry-topic=chaosreplay.telemetry.integration",
        "chaosreplay.kafka.dlq-topic=chaosreplay.telemetry.integration.dlq",
        "chaosreplay.kafka.retry.max-attempts=2",
        "chaosreplay.kafka.retry.backoff-ms=500"
})
class TelemetryKafkaIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(TelemetryKafkaIntegrationTest.class);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.1");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("chaosreplay.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @TestConfiguration
    static class DlqTestConfig {
        @Bean
        public DlqListener dlqListener() {
            return new DlqListener();
        }
    }

    static class DlqListener {
        private final List<ConsumerRecord<String, String>> records = new CopyOnWriteArrayList<>();

        @KafkaListener(topics = "chaosreplay.telemetry.integration.dlq", groupId = "dlq-test-listener-group")
        public void onMessage(ConsumerRecord<String, String> record) {
            log.info("Test DLQ listener captured record: topic={}, partition={}, offset={}, key={}",
                    record.topic(), record.partition(), record.offset(), record.key());
            records.add(record);
        }

        public List<ConsumerRecord<String, String>> getRecords() {
            return records;
        }
    }

    @Autowired
    private TelemetryKafkaProducer producer;

    @Autowired
    private TelemetryService telemetryService;

    @Autowired
    private TelemetryEventRepository telemetryRepository;

    @Autowired
    private TraceCorrelationService traceCorrelationService;

    @Autowired
    private IncidentTimelineService incidentTimelineService;

    @Autowired
    private IncidentInvestigationService incidentInvestigationService;

    @Autowired
    private DlqListener dlqListener;

    @Test
    @DisplayName("1. Valid Kafka message is consumed and persisted into PostgreSQL")
    void validMessage_consumedAndPersisted() {
        String eventId = "evt-k-01-" + UUID.randomUUID();
        String traceId = "trace-k-01-" + UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-10-18T10:00:00.000Z");

        TelemetryMessage message = new TelemetryMessage(
                eventId,
                timestamp,
                "order-service",
                "pod-ord-1",
                EventType.REQUEST,
                Severity.INFO,
                traceId,
                "req-k-01",
                "POST /order",
                "Order created successfully",
                Map.of("customerId", "cust-99")
        );

        producer.sendEventSync(message, Duration.ofSeconds(5));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            TelemetryEvent event = telemetryRepository.findByEventId(eventId).orElse(null);
            assertThat(event).isNotNull();
            assertThat(event.getServiceName()).isEqualTo("order-service");
            assertThat(event.getTraceId()).isEqualTo(traceId);
            assertThat(event.getEventType()).isEqualTo(EventType.REQUEST);
            assertThat(event.getSeverity()).isEqualTo(Severity.INFO);
            assertThat(event.getMetadata()).containsEntry("customerId", "cust-99");
        });
    }

    @Test
    @DisplayName("2. Persisted Kafka event is visible through existing Trace Correlation API")
    void persistedEvent_visibleThroughTraceApi() {
        String eventId = "evt-k-02-" + UUID.randomUUID();
        String traceId = "trace-k-02-" + UUID.randomUUID();

        TelemetryMessage message = new TelemetryMessage(
                eventId,
                Instant.parse("2026-10-18T10:01:00.000Z"),
                "inventory-service",
                null,
                EventType.DATABASE,
                Severity.INFO,
                traceId,
                null,
                "SELECT stock",
                "Stock query executed",
                null
        );

        producer.sendEventSync(message, Duration.ofSeconds(5));

        await().atMost(10, TimeUnit.SECONDS)
                .ignoreExceptionsInstanceOf(ResourceNotFoundException.class)
                .untilAsserted(() -> {
            TraceCorrelationResponse trace = traceCorrelationService.getTraceCorrelation(traceId);
            assertThat(trace).isNotNull();
            assertThat(trace.events()).hasSize(1);
            assertThat(trace.events().get(0).eventId()).isEqualTo(eventId);
            assertThat(trace.events().get(0).serviceName()).isEqualTo("inventory-service");
        });
    }

    @Test
    @DisplayName("3. Duplicate message delivery is absorbed idempotently without creating duplicate rows")
    void duplicateMessage_doesNotCreateSecondRow() {
        String eventId = "evt-k-dup-" + UUID.randomUUID();
        String traceId = "trace-k-dup-" + UUID.randomUUID();

        TelemetryMessage message = new TelemetryMessage(
                eventId,
                Instant.parse("2026-10-18T10:02:00.000Z"),
                "billing-service",
                null,
                EventType.REQUEST,
                Severity.INFO,
                traceId,
                null,
                "POST /charge",
                "Charge",
                null
        );

        // First delivery
        producer.sendEventSync(message, Duration.ofSeconds(5));
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(telemetryRepository.findByEventId(eventId)).isPresent()
        );

        long countBefore = telemetryRepository.count();

        // Second delivery of identical message
        producer.sendEventSync(message, Duration.ofSeconds(5));

        // Sleep briefly to allow consumer to process second delivery
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertThat(telemetryRepository.count()).isEqualTo(countBefore);
        assertThat(telemetryRepository.findAllByTraceIdOrderByTimestampAscEventIdAsc(traceId)).hasSize(1);
    }

    @Test
    @DisplayName("4. Two distinct events for the same trace are both persisted in chronological order")
    void multipleEventsForSameTrace_bothPersistedChronologically() {
        String traceId = "trace-k-multi-" + UUID.randomUUID();
        Instant t0 = Instant.parse("2026-10-18T10:05:00.000Z");
        Instant t1 = Instant.parse("2026-10-18T10:05:00.040Z");

        TelemetryMessage msg1 = new TelemetryMessage(
                "evt-k-m1-" + UUID.randomUUID(), t0, "api-gateway", null,
                EventType.REQUEST, Severity.INFO, traceId, null, "POST /api", "Start", null
        );
        TelemetryMessage msg2 = new TelemetryMessage(
                "evt-k-m2-" + UUID.randomUUID(), t1, "auth-service", null,
                EventType.REQUEST, Severity.INFO, traceId, null, "GET /verify", "Verified", null
        );

        producer.sendEventSync(msg1, Duration.ofSeconds(5));
        producer.sendEventSync(msg2, Duration.ofSeconds(5));

        await().atMost(10, TimeUnit.SECONDS)
                .ignoreExceptionsInstanceOf(ResourceNotFoundException.class)
                .untilAsserted(() -> {
            TraceCorrelationResponse trace = traceCorrelationService.getTraceCorrelation(traceId);
            assertThat(trace.events()).hasSize(2);
            assertThat(trace.events().get(0).eventId()).isEqualTo(msg1.eventId());
            assertThat(trace.events().get(1).eventId()).isEqualTo(msg2.eventId());
        });
    }

    @Test
    @DisplayName("5. Malformed payload does not crash consumer and is routed to Dead-Letter Queue (DLQ)")
    void malformedPayload_routedToDlq() {
        String malformedJson = "{not-a-valid-json-payload-at-all";

        producer.sendRaw("chaosreplay.telemetry.integration", "bad-key-01", malformedJson);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(dlqListener.getRecords())
                    .anyMatch(rec -> rec.value().contains("not-a-valid-json")
                            && (rec.headers().lastHeader("kafka_dlt-original-topic") != null
                            || rec.headers().lastHeader("kafka_dlt-exception-message") != null));
        });
    }

    @Test
    @DisplayName("6. Non-retryable invalid message (blank eventId) routes directly to DLQ")
    void invalidMessage_routesDirectlyToDlq() {
        // Message with blank eventId and null timestamp violates validation
        String invalidPayload = "{\"eventId\":\"\",\"timestamp\":null,\"serviceName\":\"test\",\"eventType\":\"REQUEST\",\"severity\":\"INFO\"}";

        producer.sendRaw("chaosreplay.telemetry.integration", "invalid-key-02", invalidPayload);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(dlqListener.getRecords())
                    .anyMatch(rec -> (rec.value().contains("invalid-key-02") || rec.value().contains("\"eventId\":\"\""))
                            && (rec.headers().lastHeader("kafka_dlt-original-topic") != null
                            || rec.headers().lastHeader("kafka_dlt-exception-message") != null));
        });
    }

    @Test
    @DisplayName("7. Kafka ingestion and REST ingestion coexist seamlessly")
    void coexistence_kafkaAndRestIngestion() {
        String traceId = "trace-k-coexist-" + UUID.randomUUID();
        Instant t0 = Instant.parse("2026-10-18T10:10:00.000Z");
        Instant t1 = Instant.parse("2026-10-18T10:10:00.025Z");

        // 1. Ingest event 1 via REST/Service path
        CreateTelemetryEventRequest restRequest = new CreateTelemetryEventRequest(
                "evt-co-rest-" + UUID.randomUUID(), t0, "api-gateway", null,
                EventType.REQUEST, Severity.INFO, traceId, null, "POST /submit", "Rest call", null
        );
        telemetryService.ingestEvent(restRequest);

        // 2. Ingest event 2 via Kafka path
        TelemetryMessage kafkaMessage = new TelemetryMessage(
                "evt-co-kafka-" + UUID.randomUUID(), t1, "worker-service", null,
                EventType.REQUEST, Severity.INFO, traceId, null, "POST /process", "Kafka call", null
        );
        producer.sendEventSync(kafkaMessage, Duration.ofSeconds(5));

        // 3. Verify both are correlated together
        await().atMost(10, TimeUnit.SECONDS)
                .ignoreExceptionsInstanceOf(ResourceNotFoundException.class)
                .untilAsserted(() -> {
            TraceCorrelationResponse trace = traceCorrelationService.getTraceCorrelation(traceId);
            assertThat(trace.events()).hasSize(2);
            assertThat(trace.events().get(0).eventId()).isEqualTo(restRequest.eventId());
            assertThat(trace.events().get(1).eventId()).isEqualTo(kafkaMessage.eventId());
        });
    }

    @Test
    @DisplayName("8. Incident Timeline & Consolidated Investigation work seamlessly for Kafka-ingested traces")
    void incidentTimelineAndInvestigation_workForKafkaTrace() {
        String traceId = "trace-k-incident-" + UUID.randomUUID();
        Instant t0 = Instant.parse("2026-10-18T10:20:00.000Z");
        Instant t1 = Instant.parse("2026-10-18T10:20:00.080Z");
        Instant t2 = Instant.parse("2026-10-18T10:20:00.120Z");

        TelemetryMessage msg1 = new TelemetryMessage(
                "evt-inc-k1-" + UUID.randomUUID(), t0, "api-gateway", null,
                EventType.REQUEST, Severity.INFO, traceId, null, "POST /checkout", "Start", null
        );
        TelemetryMessage msg2 = new TelemetryMessage(
                "evt-inc-k2-" + UUID.randomUUID(), t1, "inventory-service", null,
                EventType.DATABASE, Severity.ERROR, traceId, null, "UPDATE stock", "Lock wait timeout", null
        );
        TelemetryMessage msg3 = new TelemetryMessage(
                "evt-inc-k3-" + UUID.randomUUID(), t2, "order-service", null,
                EventType.REQUEST, Severity.ERROR, traceId, null, "POST /orders", "Inventory failed", null
        );

        producer.sendEventSync(msg1, Duration.ofSeconds(5));
        producer.sendEventSync(msg2, Duration.ofSeconds(5));
        producer.sendEventSync(msg3, Duration.ofSeconds(5));

        await().atMost(10, TimeUnit.SECONDS)
                .ignoreExceptionsInstanceOf(ResourceNotFoundException.class)
                .untilAsserted(() -> {
            IncidentTimelineResponse timeline = incidentTimelineService.getTimeline(traceId);
            assertThat(timeline).isNotNull();
            assertThat(timeline.eventCount()).isEqualTo(3);
            assertThat(timeline.failureCount()).isEqualTo(2);
            assertThat(timeline.failureObserved()).isTrue();
            assertThat(timeline.durationMs()).isEqualTo(120);

            List<IncidentEntryCategory> categories = timeline.timeline().stream()
                    .map(com.chaosreplay.api.dto.IncidentTimelineEntryResponse::entryCategory)
                    .toList();
            assertThat(categories).containsExactly(
                    IncidentEntryCategory.NORMAL_EVENT,
                    IncidentEntryCategory.DATABASE_FAILURE,
                    IncidentEntryCategory.DOWNSTREAM_ERROR
            );

            IncidentInvestigationResponse investigation = incidentInvestigationService.getInvestigation(traceId);
            assertThat(investigation).isNotNull();
            assertThat(investigation.timeline().eventCount()).isEqualTo(3);
        });
    }
}
