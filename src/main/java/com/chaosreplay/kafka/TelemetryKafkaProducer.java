package com.chaosreplay.kafka;

import com.chaosreplay.config.KafkaTelemetryProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Producer service publishing telemetry messages to Apache Kafka.
 * Isolates transport/messaging concerns from persistence and application logic.
 */
@Service
@ConditionalOnProperty(name = "chaosreplay.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class TelemetryKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryKafkaProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTelemetryProperties properties;
    private final ObjectMapper objectMapper;

    public TelemetryKafkaProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaTelemetryProperties properties,
            ObjectMapper objectMapper
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes a telemetry message asynchronously to the configured telemetry topic.
     * Uses the eventId as the partition key.
     *
     * @param message telemetry event payload
     * @return completable future of the send result
     */
    public CompletableFuture<SendResult<String, Object>> sendEvent(TelemetryMessage message) {
        Objects.requireNonNull(message, "TelemetryMessage must not be null");
        String topic = properties.getTelemetryTopic();
        String key = message.eventId();

        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize TelemetryMessage to JSON: " + e.getMessage(), e);
        }

        log.debug("Publishing telemetry message to Kafka [topic={}, eventId={}, traceId={}]",
                topic, message.eventId(), message.traceId());

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, payload);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish telemetry message to Kafka [topic={}, eventId={}, traceId={}]: {}",
                        topic, message.eventId(), message.traceId(), ex.getMessage(), ex);
            } else if (result != null) {
                log.info("Published telemetry message to Kafka [topic={}, partition={}, offset={}, eventId={}, traceId={}]",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        message.eventId(),
                        message.traceId());
            }
        });

        return future;
    }

    /**
     * Publishes a telemetry message synchronously with a bounded timeout.
     *
     * @param message telemetry event payload
     * @param timeout maximum duration to wait for broker acknowledgment
     * @return verified send result
     */
    public SendResult<String, Object> sendEventSync(TelemetryMessage message, Duration timeout) {
        try {
            return sendEvent(message).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Kafka publish acknowledgment", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to publish telemetry event to Kafka: " + e.getCause().getMessage(), e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out waiting for Kafka publish acknowledgment after " + timeout.toMillis() + "ms", e);
        }
    }

    /**
     * Publishes a raw payload to a specified topic and key.
     * Primarily used for DLQ verification, malformed payload testing, and arbitrary topic emission.
     *
     * @param topic   target Kafka topic
     * @param key     message key
     * @param payload message payload (String or Object serialized to JSON)
     * @return completable future of the send result
     */
    public CompletableFuture<SendResult<String, Object>> sendRaw(String topic, String key, Object payload) {
        log.debug("Publishing raw message to Kafka [topic={}, key={}]", topic, key);
        String stringPayload;
        if (payload instanceof String s) {
            stringPayload = s;
        } else {
            try {
                stringPayload = objectMapper.writeValueAsString(payload);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Failed to serialize raw payload to JSON: " + e.getMessage(), e);
            }
        }

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, stringPayload);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish raw message to Kafka [topic={}, key={}]: {}", topic, key, ex.getMessage(), ex);
            } else if (result != null) {
                log.info("Published raw message to Kafka [topic={}, partition={}, offset={}, key={}]",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        key);
            }
        });

        return future;
    }
}
