package com.chaosreplay.kafka;

import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.Severity;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaReliabilityHardeningTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("TelemetryMessage with valid inputs produces no constraint violations")
    void validMessage_passesValidation() {
        TelemetryMessage message = new TelemetryMessage(
                "evt-kafka-valid-1",
                Instant.now(),
                "order-service",
                "inst-1",
                EventType.REQUEST,
                Severity.INFO,
                "trace-1",
                "req-1",
                "POST /orders",
                "Order created",
                Map.of("key1", "val1")
        );

        Set<ConstraintViolation<TelemetryMessage>> violations = validator.validate(message);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("TelemetryMessage with metadata exceeding 50 entries fails validation")
    void metadataExceeding50_failsValidation() {
        Map<String, Object> excessive = new HashMap<>();
        for (int i = 0; i < 51; i++) {
            excessive.put("k_" + i, "v_" + i);
        }

        TelemetryMessage message = new TelemetryMessage(
                "evt-kafka-invalid-1",
                Instant.now(),
                "order-service",
                null,
                EventType.REQUEST,
                Severity.INFO,
                "trace-1",
                null,
                null,
                null,
                excessive
        );

        Set<ConstraintViolation<TelemetryMessage>> violations = validator.validate(message);
        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .anyMatch(v -> v.getMessage().contains("metadata must not contain more than 50 entries"));
    }

    @Test
    @DisplayName("TelemetryMessage with invalid eventId characters fails validation")
    void invalidCharactersInEventId_failsValidation() {
        TelemetryMessage message = new TelemetryMessage(
                "evt;DROP TABLE telemetry;--",
                Instant.now(),
                "order-service",
                null,
                EventType.REQUEST,
                Severity.INFO,
                "trace-1",
                null,
                null,
                null,
                Map.of()
        );

        Set<ConstraintViolation<TelemetryMessage>> violations = validator.validate(message);
        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .anyMatch(v -> v.getMessage().contains("eventId contains invalid characters"));
    }

    @Test
    @DisplayName("TelemetryMessage with blank metadata key fails validation")
    void blankMetadataKey_failsValidation() {
        TelemetryMessage message = new TelemetryMessage(
                "evt-kafka-invalid-3",
                Instant.now(),
                "order-service",
                null,
                EventType.REQUEST,
                Severity.INFO,
                "trace-1",
                null,
                null,
                null,
                Map.of("   ", "val")
        );

        Set<ConstraintViolation<TelemetryMessage>> violations = validator.validate(message);
        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .anyMatch(v -> v.getMessage().contains("metadata keys must be non-blank"));
    }
}

