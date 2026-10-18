package com.chaosreplay.kafka;

import com.chaosreplay.api.dto.CreateTelemetryEventRequest;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.Severity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TelemetryMessage Unit Tests")
class TelemetryMessageTest {

    private Validator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("Valid TelemetryMessage passes validation and converts to CreateTelemetryEventRequest")
    void validMessage_passesValidation() {
        TelemetryMessage msg = new TelemetryMessage(
                "evt-101",
                Instant.parse("2026-10-18T10:00:00Z"),
                "order-service",
                "instance-1",
                EventType.REQUEST,
                Severity.INFO,
                "trace-101",
                "req-101",
                "POST /orders",
                "Order created",
                Map.of("customerId", "cust-123")
        );

        Set<ConstraintViolation<TelemetryMessage>> violations = validator.validate(msg);
        assertThat(violations).isEmpty();

        CreateTelemetryEventRequest request = msg.toCreateRequest();
        assertThat(request.eventId()).isEqualTo("evt-101");
        assertThat(request.serviceName()).isEqualTo("order-service");
        assertThat(request.eventType()).isEqualTo(EventType.REQUEST);
        assertThat(request.severity()).isEqualTo(Severity.INFO);
        assertThat(request.traceId()).isEqualTo("trace-101");
        assertThat(request.metadata()).containsEntry("customerId", "cust-123");

        TelemetryMessage fromReq = TelemetryMessage.fromCreateRequest(request);
        assertThat(fromReq).isEqualTo(msg);
    }

    @Test
    @DisplayName("Invalid TelemetryMessage fails validation when mandatory fields are missing")
    void invalidMessage_failsValidation() {
        TelemetryMessage msg = new TelemetryMessage(
                "", // Blank eventId
                null, // Null timestamp
                "   ", // Blank serviceName
                null,
                null, // Null eventType
                null, // Null severity
                null,
                null,
                null,
                null,
                null
        );

        Set<ConstraintViolation<TelemetryMessage>> violations = validator.validate(msg);
        assertThat(violations).hasSize(5);
        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("eventId", "timestamp", "serviceName", "eventType", "severity");
    }

    @Test
    @DisplayName("TelemetryMessage serializes to and deserializes from JSON correctly")
    void jsonRoundTrip_preservesAllFields() throws Exception {
        TelemetryMessage msg = new TelemetryMessage(
                "evt-json-01",
                Instant.parse("2026-10-18T10:30:00.123456Z"),
                "payment-service",
                "pay-pod-2",
                EventType.DATABASE,
                Severity.ERROR,
                "trace-json-01",
                "req-json-01",
                "UPDATE accounts",
                "Deadlock detected",
                Map.of("retryCount", 2)
        );

        String json = objectMapper.writeValueAsString(msg);
        TelemetryMessage deserialized = objectMapper.readValue(json, TelemetryMessage.class);

        assertThat(deserialized.eventId()).isEqualTo("evt-json-01");
        assertThat(deserialized.serviceName()).isEqualTo("payment-service");
        assertThat(deserialized.eventType()).isEqualTo(EventType.DATABASE);
        assertThat(deserialized.severity()).isEqualTo(Severity.ERROR);
        assertThat(deserialized.traceId()).isEqualTo("trace-json-01");
        assertThat(deserialized.operation()).isEqualTo("UPDATE accounts");
        assertThat(deserialized.metadata()).containsEntry("retryCount", 2);
    }
}

