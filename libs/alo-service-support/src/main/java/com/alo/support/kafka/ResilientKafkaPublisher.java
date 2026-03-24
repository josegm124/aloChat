package com.alo.support.kafka;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ResilientKafkaPublisher {
    private static final Logger log = LoggerFactory.getLogger(ResilientKafkaPublisher.class);

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final KafkaResilienceProperties properties;

    public ResilientKafkaPublisher(
            KafkaTemplate<Object, Object> kafkaTemplate,
            CircuitBreakerRegistry circuitBreakerRegistry,
            KafkaResilienceProperties properties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.properties = properties;
    }

    public void send(String topic, String key, Object payload) {
        ProducerRecord<Object, Object> record = new ProducerRecord<>(topic, key, payload);
        send(record);
    }

    public void send(ProducerRecord<Object, Object> record) {
        String topic = record.topic();
        Object key = record.key();
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName(topic));
        Callable<Object> protectedSend = CircuitBreaker.decorateCallable(
                circuitBreaker,
                () -> kafkaTemplate.send(record)
                        .get(properties.getProducer().getSendTimeoutMs(), TimeUnit.MILLISECONDS)
        );

        try {
            protectedSend.call();
            log.debug("kafka publish succeeded topic={} key={}", topic, key);
        } catch (CallNotPermittedException exception) {
            throw new IllegalStateException("Kafka publish circuit breaker open for topic=" + topic, exception);
        } catch (TimeoutException exception) {
            throw new IllegalStateException("Kafka publish timed out for topic=" + topic, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka publish interrupted for topic=" + topic, exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Kafka publish failed for topic=" + topic, exception.getCause());
        } catch (Exception exception) {
            throw new IllegalStateException("Kafka publish failed for topic=" + topic, exception);
        }
    }

    private String circuitBreakerName(String topic) {
        return properties.getProducer().getCircuitBreakerName() + "." + topic.replace('.', '_');
    }
}
