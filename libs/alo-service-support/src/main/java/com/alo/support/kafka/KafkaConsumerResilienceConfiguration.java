package com.alo.support.kafka;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.converter.ConversionException;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.invocation.MethodArgumentResolutionException;

@Configuration
@EnableConfigurationProperties(KafkaResilienceProperties.class)
public class KafkaConsumerResilienceConfiguration {
    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerResilienceConfiguration.class);

    @Bean
    public DefaultErrorHandler kafkaDefaultErrorHandler(
            KafkaOperations<Object, Object> kafkaOperations,
            KafkaResilienceProperties properties
    ) {
        ExponentialBackOffWithMaxRetries backOff =
                new ExponentialBackOffWithMaxRetries(Math.max(properties.getConsumer().getMaxAttempts() - 1, 0));
        backOff.setInitialInterval(properties.getConsumer().getInitialIntervalMs());
        backOff.setMultiplier(properties.getConsumer().getMultiplier());
        backOff.setMaxInterval(properties.getConsumer().getMaxIntervalMs());

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (record, exception) -> {
                    String destinationTopic = properties.getConsumer().getDlqTopic();
                    log.error(
                            "routing message to dlq topic={} originalTopic={} key={} partition={} error={}",
                            destinationTopic,
                            record.topic(),
                            record.key(),
                            record.partition(),
                            exception.getMessage()
                    );
                    return new TopicPartition(destinationTopic, record.partition());
                }
        );
        recoverer.setHeadersFunction((record, exception) -> {
            int nextAttempt = RetryTopicHeaders.retryAttempt(record.headers()) + 1;
            RecordHeaders headers = new RecordHeaders();
            headers.add(RetryTopicHeaders.RETRY_ATTEMPT, RetryTopicHeaders.bytes(Integer.toString(nextAttempt)));
            headers.add(RetryTopicHeaders.RETRY_STAGE, new byte[0]);
            return headers;
        });

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.setCommitRecovered(true);
        errorHandler.addNotRetryableExceptions(
                DeserializationException.class,
                MessageConversionException.class,
                ConversionException.class,
                MethodArgumentResolutionException.class,
                ClassCastException.class
        );
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> log.warn(
                "retrying kafka message topic={} key={} attempt={} error={}",
                record.topic(),
                record.key(),
                deliveryAttempt,
                ex.getMessage()
        ));
        return errorHandler;
    }
}
