package com.chaosreplay.kafka;

import com.chaosreplay.api.dto.CreateTelemetryEventRequest;
import com.chaosreplay.exception.DuplicateEventException;
import com.chaosreplay.service.TelemetryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Spring Kafka consumer ingesting telemetry events from the configured Kafka topic.
 * Delegates to the existing {@link TelemetryService} to enforce identical validation,
 * persistence, trace correlation, and duplicate-event handling.
 */
import com.chaosreplay.metrics.ChaosReplayMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@Service
@ConditionalOnProperty(name = "chaosreplay.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class TelemetryKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryKafkaConsumer.class);

    private final TelemetryService telemetryService;
    private final Validator validator;
    private final ObjectMapper objectMapper;
    private final ChaosReplayMetrics metrics;

    public TelemetryKafkaConsumer(
            TelemetryService telemetryService,
            Validator validator,
            ObjectMapper objectMapper
    ) {
        this(telemetryService, validator, objectMapper, new ChaosReplayMetrics(new SimpleMeterRegistry()));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public TelemetryKafkaConsumer(
            TelemetryService telemetryService,
            Validator validator,
            ObjectMapper objectMapper,
            ChaosReplayMetrics metrics
    ) {
        this.telemetryService = telemetryService;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.metrics = metrics != null ? metrics : new ChaosReplayMetrics(new SimpleMeterRegistry());
    }

    /**
     * Consumes telemetry records from Kafka, validates payload integrity, delegates to
     * {@link TelemetryService#ingestEvent(CreateTelemetryEventRequest, String)}, and gracefully
     * absorbs duplicate events without creating duplicate rows or crashing the consumer.
     *
     * @param record consumed Kafka record containing payload and transport metadata
     */
    @KafkaListener(
            topics = "${chaosreplay.kafka.telemetry-topic:chaosreplay.telemetry}",
            groupId = "${chaosreplay.kafka.consumer-group:chaosreplay-telemetry-group}"
    )
    public void consumeTelemetry(ConsumerRecord<String, Object> record) {
        long startNanos = System.nanoTime();
        Object rawPayload = record.value();

        log.debug("Received telemetry record from Kafka [topic={}, partition={}, offset={}, key={}]",
                record.topic(), record.partition(), record.offset(), record.key());

        TelemetryMessage message;
        try {
            message = parseMessage(rawPayload, record);
            validateMessage(message, record);
        } catch (InvalidTelemetryMessageException ex) {
            long duration = System.nanoTime() - startNanos;
            metrics.recordKafkaDlq("invalid_message");
            metrics.recordKafkaConsumed("failure");
            metrics.recordKafkaConsumptionDuration("failure", duration);
            throw ex;
        }

        CreateTelemetryEventRequest request = message.toCreateRequest();

        try {
            telemetryService.ingestEvent(request);
            long duration = System.nanoTime() - startNanos;
            metrics.recordKafkaConsumed("success");
            metrics.recordKafkaConsumptionDuration("success", duration);
            log.info("Successfully ingested Kafka telemetry event [eventId={}, traceId={}, topic={}, partition={}, offset={}, durationMs={}]",
                    message.eventId(), message.traceId(), record.topic(), record.partition(), record.offset(), duration / 1_000_000);
        } catch (DuplicateEventException ex) {
            // Idempotent duplicate delivery: log structured warning and commit offset without failure
            long duration = System.nanoTime() - startNanos;
            metrics.recordKafkaConsumed("duplicate");
            metrics.recordKafkaConsumptionDuration("duplicate", duration);
            log.warn("Duplicate telemetry event ignored from Kafka [eventId={}, traceId={}, topic={}, partition={}, offset={}, durationMs={}]: {}",
                    message.eventId(), message.traceId(), record.topic(), record.partition(), record.offset(), duration / 1_000_000, ex.getMessage());
        } catch (Exception ex) {
            long duration = System.nanoTime() - startNanos;
            metrics.recordKafkaConsumed("failure");
            metrics.recordKafkaConsumptionDuration("failure", duration);
            log.error("Failed to ingest telemetry event from Kafka [eventId={}, traceId={}, topic={}, partition={}, offset={}, durationMs={}]: {}",
                    message.eventId(), message.traceId(), record.topic(), record.partition(), record.offset(), duration / 1_000_000, ex.getMessage(), ex);
            throw ex;
        }
    }

    private TelemetryMessage parseMessage(Object rawPayload, ConsumerRecord<String, Object> record) {
        if (rawPayload == null) {
            throw new InvalidTelemetryMessageException(String.format(
                    "Null payload received from Kafka [topic=%s, partition=%d, offset=%d]",
                    record.topic(), record.partition(), record.offset()
            ));
        }

        if (rawPayload instanceof TelemetryMessage tm) {
            return tm;
        }

        if (rawPayload instanceof String str) {
            try {
                return objectMapper.readValue(str, TelemetryMessage.class);
            } catch (Exception e) {
                throw new InvalidTelemetryMessageException(String.format(
                        "Malformed JSON payload received from Kafka [topic=%s, partition=%d, offset=%d]: %s",
                        record.topic(), record.partition(), record.offset(), e.getMessage()
                ), e);
            }
        }

        if (rawPayload instanceof byte[] bytes) {
            try {
                return objectMapper.readValue(bytes, TelemetryMessage.class);
            } catch (Exception e) {
                throw new InvalidTelemetryMessageException(String.format(
                        "Malformed binary payload received from Kafka [topic=%s, partition=%d, offset=%d]: %s",
                        record.topic(), record.partition(), record.offset(), e.getMessage()
                ), e);
            }
        }

        try {
            return objectMapper.convertValue(rawPayload, TelemetryMessage.class);
        } catch (Exception e) {
            throw new InvalidTelemetryMessageException(String.format(
                    "Unsupported payload type [%s] received from Kafka [topic=%s, partition=%d, offset=%d]: %s",
                    rawPayload.getClass().getName(), record.topic(), record.partition(), record.offset(), e.getMessage()
            ), e);
        }
    }

    private void validateMessage(TelemetryMessage message, ConsumerRecord<String, Object> record) {
        Set<ConstraintViolation<TelemetryMessage>> violations = validator.validate(message);
        if (!violations.isEmpty()) {
            String errorSummary = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));

            throw new InvalidTelemetryMessageException(String.format(
                    "Validation failed for telemetry message from Kafka [topic=%s, partition=%d, offset=%d]: %s",
                    record.topic(), record.partition(), record.offset(), errorSummary
            ));
        }
    }
}

