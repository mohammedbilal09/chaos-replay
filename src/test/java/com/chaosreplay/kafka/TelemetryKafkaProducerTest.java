package com.chaosreplay.kafka;

import com.chaosreplay.config.KafkaTelemetryProperties;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.Severity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TelemetryKafkaProducer Unit Tests")
class TelemetryKafkaProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private KafkaTelemetryProperties properties;
    private ObjectMapper objectMapper;
    private TelemetryKafkaProducer producer;

    @BeforeEach
    void setUp() {
        properties = new KafkaTelemetryProperties();
        properties.setTelemetryTopic("chaosreplay.telemetry");
        properties.setDlqTopic("chaosreplay.telemetry.dlq");
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        producer = new TelemetryKafkaProducer(kafkaTemplate, properties, objectMapper);
    }

    @Test
    @DisplayName("sendEvent successfully publishes message using eventId as partition key")
    void sendEvent_publishesWithEventIdKey() {
        TelemetryMessage message = new TelemetryMessage(
                "evt-p1",
                Instant.now(),
                "order-service",
                null,
                EventType.REQUEST,
                Severity.INFO,
                "trace-p1",
                null,
                "POST /order",
                "Success",
                null
        );

        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("chaosreplay.telemetry", 0), 0, 0, System.currentTimeMillis(), 0, 0
        );
        ProducerRecord<String, Object> record = new ProducerRecord<>("chaosreplay.telemetry", "evt-p1", "payload");
        SendResult<String, Object> sendResult = new SendResult<>(record, metadata);

        when(kafkaTemplate.send(eq("chaosreplay.telemetry"), eq("evt-p1"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        CompletableFuture<SendResult<String, Object>> future = producer.sendEvent(message);

        assertThat(future).isCompletedWithValue(sendResult);
        verify(kafkaTemplate).send(eq("chaosreplay.telemetry"), eq("evt-p1"), anyString());
    }

    @Test
    @DisplayName("sendEventSync returns result synchronously when broker acknowledges within timeout")
    void sendEventSync_returnsResult() {
        TelemetryMessage message = new TelemetryMessage(
                "evt-p2",
                Instant.now(),
                "payment-service",
                null,
                EventType.DATABASE,
                Severity.ERROR,
                "trace-p2",
                null,
                "UPDATE accounts",
                "Failed",
                null
        );

        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("chaosreplay.telemetry", 0), 0, 42, System.currentTimeMillis(), 0, 0
        );
        ProducerRecord<String, Object> record = new ProducerRecord<>("chaosreplay.telemetry", "evt-p2", "payload");
        SendResult<String, Object> sendResult = new SendResult<>(record, metadata);

        when(kafkaTemplate.send(eq("chaosreplay.telemetry"), eq("evt-p2"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        SendResult<String, Object> result = producer.sendEventSync(message, Duration.ofSeconds(2));

        assertThat(result).isNotNull();
        assertThat(result.getRecordMetadata().offset()).isEqualTo(42);
    }

    @Test
    @DisplayName("sendEventSync throws IllegalStateException when Kafka broker send fails")
    void sendEventSync_brokerFailure_throwsIllegalStateException() {
        TelemetryMessage message = new TelemetryMessage(
                "evt-p3",
                Instant.now(),
                "auth-service",
                null,
                EventType.REQUEST,
                Severity.INFO,
                "trace-p3",
                null,
                "GET /token",
                "Auth",
                null
        );

        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Broker unreachable"));

        when(kafkaTemplate.send(any(), any(), any())).thenReturn(failedFuture);

        assertThatThrownBy(() -> producer.sendEventSync(message, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to publish telemetry event to Kafka");
    }

    @Test
    @DisplayName("sendRaw publishes arbitrary payload to specified topic")
    void sendRaw_publishesToSpecifiedTopic() {
        when(kafkaTemplate.send(eq("test-topic"), eq("test-key"), eq("raw-content")))
                .thenReturn(new CompletableFuture<>());

        producer.sendRaw("test-topic", "test-key", "raw-content");

        verify(kafkaTemplate).send("test-topic", "test-key", "raw-content");
    }
}
