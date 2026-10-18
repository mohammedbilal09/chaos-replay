package com.chaosreplay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Type-safe configuration properties for Kafka telemetry ingestion.
 */
@Component
@ConfigurationProperties(prefix = "chaosreplay.kafka")
public class KafkaTelemetryProperties {

    /**
     * Master toggle for Kafka messaging components (listeners, producers).
     */
    private boolean enabled = true;

    /**
     * Comma-separated list of Kafka broker addresses.
     */
    private String bootstrapServers = "localhost:9092";

    /**
     * Primary topic for incoming distributed telemetry events.
     */
    private String telemetryTopic = "chaosreplay.telemetry";

    /**
     * Dead-letter queue topic for failed, exhausted, or unprocessable messages.
     */
    private String dlqTopic = "chaosreplay.telemetry.dlq";

    /**
     * Consumer group identifier.
     */
    private String consumerGroup = "chaosreplay-telemetry-group";

    /**
     * Consumer concurrency (number of listener threads).
     */
    private int consumerConcurrency = 1;

    /**
     * Default offset reset policy when no initial offset is present ("earliest", "latest").
     */
    private String autoOffsetReset = "earliest";

    /**
     * Retry policy configuration.
     */
    private Retry retry = new Retry();

    public static class Retry {
        private int maxAttempts = 3;
        private long backoffMs = 1000L;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getBackoffMs() {
            return backoffMs;
        }

        public void setBackoffMs(long backoffMs) {
            this.backoffMs = backoffMs;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public String getTelemetryTopic() {
        return telemetryTopic;
    }

    public void setTelemetryTopic(String telemetryTopic) {
        this.telemetryTopic = telemetryTopic;
    }

    public String getDlqTopic() {
        return dlqTopic;
    }

    public void setDlqTopic(String dlqTopic) {
        this.dlqTopic = dlqTopic;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public int getConsumerConcurrency() {
        return consumerConcurrency;
    }

    public void setConsumerConcurrency(int consumerConcurrency) {
        this.consumerConcurrency = consumerConcurrency;
    }

    public String getAutoOffsetReset() {
        return autoOffsetReset;
    }

    public void setAutoOffsetReset(String autoOffsetReset) {
        this.autoOffsetReset = autoOffsetReset;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }
}

