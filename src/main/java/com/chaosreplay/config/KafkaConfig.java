package com.chaosreplay.config;

import com.chaosreplay.kafka.InvalidTelemetryMessageException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring Kafka configuration establishing producer templates, listener container factories,
 * retry policies, and dead-letter publishing recoverers.
 */
@Configuration
@ConditionalOnProperty(name = "chaosreplay.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {

    private final KafkaTelemetryProperties properties;

    public KafkaConfig(KafkaTelemetryProperties properties) {
        this.properties = properties;
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10000);
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, properties.getConsumerGroup());
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, properties.getAutoOffsetReset());
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        // Recoverer routes exhausted or unrecoverable records to the DLQ topic
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(properties.getDlqTopic(), 0)
        );

        int maxAttempts = properties.getRetry().getMaxAttempts();
        long backoffMs = properties.getRetry().getBackoffMs();

        // Attempts include initial execution, so retries = maxAttempts - 1
        FixedBackOff backOff = new FixedBackOff(backoffMs, Math.max(0, maxAttempts - 1));
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // Non-retryable exceptions immediately bypass retries and go to DLQ
        errorHandler.addNotRetryableExceptions(
                InvalidTelemetryMessageException.class,
                IllegalArgumentException.class,
                MethodArgumentNotValidException.class
        );

        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            CommonErrorHandler kafkaErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.setConcurrency(properties.getConsumerConcurrency());
        return factory;
    }

    @Bean
    public NewTopic telemetryTopic() {
        return TopicBuilder.name(properties.getTelemetryTopic())
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic telemetryDlqTopic() {
        return TopicBuilder.name(properties.getDlqTopic())
                .partitions(1)
                .replicas(1)
                .build();
    }
}
