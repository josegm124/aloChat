package com.alo.support.kafka;

import com.alo.contracts.events.KafkaTopics;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "alo.kafka.resilience")
public class KafkaResilienceProperties {
    private final Consumer consumer = new Consumer();
    private final Producer producer = new Producer();
    private final RetryRouter retryRouter = new RetryRouter();

    public Consumer getConsumer() {
        return consumer;
    }

    public Producer getProducer() {
        return producer;
    }

    public RetryRouter getRetryRouter() {
        return retryRouter;
    }

    public static class Consumer {
        private int maxAttempts = 3;
        private long initialIntervalMs = 1_000L;
        private double multiplier = 2.0d;
        private long maxIntervalMs = 10_000L;
        private String dlqTopic = KafkaTopics.DLQ;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getInitialIntervalMs() {
            return initialIntervalMs;
        }

        public void setInitialIntervalMs(long initialIntervalMs) {
            this.initialIntervalMs = initialIntervalMs;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(double multiplier) {
            this.multiplier = multiplier;
        }

        public long getMaxIntervalMs() {
            return maxIntervalMs;
        }

        public void setMaxIntervalMs(long maxIntervalMs) {
            this.maxIntervalMs = maxIntervalMs;
        }

        public String getDlqTopic() {
            return dlqTopic;
        }

        public void setDlqTopic(String dlqTopic) {
            this.dlqTopic = dlqTopic;
        }
    }

    public static class Producer {
        private String circuitBreakerName = "kafkaPublisher";
        private float failureRateThreshold = 50.0f;
        private int minimumNumberOfCalls = 5;
        private int slidingWindowSize = 10;
        private int permittedCallsInHalfOpenState = 3;
        private long openStateWaitMs = 10_000L;
        private long sendTimeoutMs = 5_000L;

        public String getCircuitBreakerName() {
            return circuitBreakerName;
        }

        public void setCircuitBreakerName(String circuitBreakerName) {
            this.circuitBreakerName = circuitBreakerName;
        }

        public float getFailureRateThreshold() {
            return failureRateThreshold;
        }

        public void setFailureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        public int getMinimumNumberOfCalls() {
            return minimumNumberOfCalls;
        }

        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
            this.minimumNumberOfCalls = minimumNumberOfCalls;
        }

        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }

        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }

        public int getPermittedCallsInHalfOpenState() {
            return permittedCallsInHalfOpenState;
        }

        public void setPermittedCallsInHalfOpenState(int permittedCallsInHalfOpenState) {
            this.permittedCallsInHalfOpenState = permittedCallsInHalfOpenState;
        }

        public long getOpenStateWaitMs() {
            return openStateWaitMs;
        }

        public void setOpenStateWaitMs(long openStateWaitMs) {
            this.openStateWaitMs = openStateWaitMs;
        }

        public long getSendTimeoutMs() {
            return sendTimeoutMs;
        }

        public void setSendTimeoutMs(long sendTimeoutMs) {
            this.sendTimeoutMs = sendTimeoutMs;
        }
    }

    public static class RetryRouter {
        private long shortDelayMs = 5_000L;
        private long longDelayMs = 30_000L;

        public long getShortDelayMs() {
            return shortDelayMs;
        }

        public void setShortDelayMs(long shortDelayMs) {
            this.shortDelayMs = shortDelayMs;
        }

        public long getLongDelayMs() {
            return longDelayMs;
        }

        public void setLongDelayMs(long longDelayMs) {
            this.longDelayMs = longDelayMs;
        }
    }
}
