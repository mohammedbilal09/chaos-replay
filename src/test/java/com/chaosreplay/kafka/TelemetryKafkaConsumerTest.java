package com.chaosreplay.kafka;

import com.chaosreplay.api.dto.CreateTelemetryEventRequest;
import com.chaosreplay.api.dto.TelemetryEventResponse;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.Severity;
import com.chaosreplay.exception.DuplicateEventException;
import com.chaosreplay.service.TelemetryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TelemetryKafkaConsumer Unit Tests")
class TelemetryKafkaConsumerTest {

    @Mock
    private TelemetryService telemetryService;

    private Validator validator;
    private ObjectMapper objectMapper;
    private TelemetryKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer = new TelemetryKafkaConsumer(telemetryService, validator, objectMapper);
    }

    @Test
    @DisplayName("Valid TelemetryMessage consumes and delegates to TelemetryService")
    void consumeTelemetry_validMessage_delegatesToService() {
        TelemetryMessage message = new TelemetryMessage(
                "evt-c1",
                Instant.parse("2026-10-18T10:00:00Z"),
                "order-service",
                "pod-1",
                EventType.REQUEST,
                Severity.INFO,
                "trace-c1",
                "req-c1",
                "POST /order",
                "Success",
                Map.of("item", "widget")
        );

        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                "chaosreplay.telemetry", 0, 10L, "evt-c1", message
        );

        when(telemetryService.ingestEvent(any(CreateTelemetryEventRequest.class)))
                .thenReturn(new TelemetryEventResponse(
                        1L, "evt-c1", Instant.parse("2026-10-18T10:00:00Z"),
                        "order-service", "pod-1", EventType.REQUEST, Severity.INFO,
                        "trace-c1", "req-c1", "POST /order", "Success", Map.of("item", "widget"),
                        Instant.now()
                ));

        assertThatCode(() -> consumer.consumeTelemetry(record)).doesNotThrowAnyException();

        ArgumentCaptor<CreateTelemetryEventRequest> captor = ArgumentCaptor.forClass(CreateTelemetryEventRequest.class);
        verify(telemetryService).ingestEvent(captor.capture());

        CreateTelemetryEventRequest captured = captor.getValue();
        assertThat(captured.eventId()).isEqualTo("evt-c1");
        assertThat(captured.serviceName()).isEqualTo("order-service");
        assertThat(captured.traceId()).isEqualTo("trace-c1");
        assertThat(captured.eventType()).isEqualTo(EventType.REQUEST);
    }

    @Test
    @DisplayName("Valid JSON string consumes and deserializes into TelemetryMessage")
    void consumeTelemetry_validJsonString_deserializesAndDelegates() throws Exception {
        TelemetryMessage message = new TelemetryMessage(
                "evt-json-2",
                Instant.parse("2026-10-18T10:05:00Z"),
                "payment-service",
                null,
                EventType.DATABASE,
                Severity.ERROR,
                "trace-json-2",
                null,
                "UPDATE accounts",
                "Lock wait timeout",
                null
        );

        String jsonPayload = objectMapper.writeValueAsString(message);
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                "chaosreplay.telemetry", 0, 11L, "evt-json-2", jsonPayload
        );

        consumer.consumeTelemetry(record);

        ArgumentCaptor<CreateTelemetryEventRequest> captor = ArgumentCaptor.forClass(CreateTelemetryEventRequest.class);
        verify(telemetryService).ingestEvent(captor.capture());
        assertThat(captor.getValue().eventId()).isEqualTo("evt-json-2");
        assertThat(captor.getValue().serviceName()).isEqualTo("payment-service");
    }

    @Test
    @DisplayName("Duplicate event from Kafka is caught, logged, and treated as an idempotent no-op")
    void consumeTelemetry_duplicateEvent_absorbsExceptionGracefully() {
        TelemetryMessage message = new TelemetryMessage(
                "evt-dup-1",
                Instant.parse("2026-10-18T10:00:00Z"),
                "order-service",
                null,
                EventType.REQUEST,
                Severity.INFO,
                "trace-dup-1",
                null,
                "POST /order",
                "Order",
                null
        );

        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                "chaosreplay.telemetry", 0, 12L, "evt-dup-1", message
        );

        when(telemetryService.ingestEvent(any(CreateTelemetryEventRequest.class)))
                .thenThrow(new DuplicateEventException("evt-dup-1"));

        // Must NOT throw exception: idempotent no-op so Kafka offset commits cleanly!
        assertThatCode(() -> consumer.consumeTelemetry(record)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Invalid message with missing fields throws InvalidTelemetryMessageException")
    void consumeTelemetry_missingFields_throwsInvalidTelemetryMessageException() {
        TelemetryMessage invalidMessage = new TelemetryMessage(
                "", // Blank
                null, // Null timestamp
                "order-service",
                null,
                EventType.REQUEST,
                Severity.INFO,
                "trace-inv",
                null,
                null,
                null,
                null
        );

        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                "chaosreplay.telemetry", 0, 13L, "", invalidMessage
        );

        assertThatThrownBy(() -> consumer.consumeTelemetry(record))
                .isInstanceOf(InvalidTelemetryMessageException.class)
                .hasMessageContaining("Validation failed");
    }

    @Test
    @DisplayName("Malformed JSON payload throws InvalidTelemetryMessageException")
    void consumeTelemetry_malformedJson_throwsInvalidTelemetryMessageException() {
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                "chaosreplay.telemetry", 0, 14L, "bad-key", "{not-valid-json"
        );

        assertThatThrownBy(() -> consumer.consumeTelemetry(record))
                .isInstanceOf(InvalidTelemetryMessageException.class)
                .hasMessageContaining("Malformed JSON payload");
    }

    @Test
    @DisplayName("Null payload throws InvalidTelemetryMessageException")
    void consumeTelemetry_nullPayload_throwsInvalidTelemetryMessageException() {
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                "chaosreplay.telemetry", 0, 15L, "null-key", null
        );

        assertThatThrownBy(() -> consumer.consumeTelemetry(record))
                .isInstanceOf(InvalidTelemetryMessageException.class)
                .hasMessageContaining("Null payload received");
    }

    @Test
    @DisplayName("Transient database error from TelemetryService rethrows for Kafka retry backoff")
    void consumeTelemetry_transientServiceException_rethrowsForRetry() {
        TelemetryMessage message = new TelemetryMessage(
                "evt-err-1",
                Instant.parse("2026-10-18T10:00:00Z"),
                "order-service",
                null,
                EventType.REQUEST,
                Severity.INFO,
                "trace-err-1",
                null,
                null,
                null,
                null
        );

        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                "chaosreplay.telemetry", 0, 16L, "evt-err-1", message
        );

        when(telemetryService.ingestEvent(any(CreateTelemetryEventRequest.class)))
                .thenThrow(new RuntimeException("Database connection timeout"));

        assertThatThrownBy(() -> consumer.consumeTelemetry(record))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Database connection timeout");
    }
}

