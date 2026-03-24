package com.alo.support.kafka;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KafkaResilienceProperties.class)
public class KafkaProducerCircuitBreakerConfiguration {
    @Bean
    public CircuitBreakerRegistry kafkaCircuitBreakerRegistry(KafkaResilienceProperties properties) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(properties.getProducer().getFailureRateThreshold())
                .minimumNumberOfCalls(properties.getProducer().getMinimumNumberOfCalls())
                .slidingWindowSize(properties.getProducer().getSlidingWindowSize())
                .permittedNumberOfCallsInHalfOpenState(properties.getProducer().getPermittedCallsInHalfOpenState())
                .waitDurationInOpenState(Duration.ofMillis(properties.getProducer().getOpenStateWaitMs()))
                .slowCallRateThreshold(100.0f)
                .slowCallDurationThreshold(Duration.ofMillis(properties.getProducer().getSendTimeoutMs()))
                .build();
        return CircuitBreakerRegistry.of(config);
    }
}
