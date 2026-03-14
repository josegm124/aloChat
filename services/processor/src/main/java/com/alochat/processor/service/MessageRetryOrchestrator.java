package com.alochat.processor.service;

import com.alochat.contracts.message.MessageEnvelope;
import com.alochat.processor.port.RetryTopicPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MessageRetryOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MessageRetryOrchestrator.class);
    private static final String TARGET_SERVICE = "processor";

    private final MessageProcessingService processingService;
    private final RetryTopicPublisher retryTopicPublisher;
    private final long shortDelayMs;
    private final long longDelayMs;
    private final String normalizedTopic;
    private final String shortRetryTopic;
    private final String longRetryTopic;

    public MessageRetryOrchestrator(
            MessageProcessingService processingService,
            RetryTopicPublisher retryTopicPublisher,
            @Value("${alochat.kafka.retry-delay.short-ms}") long shortDelayMs,
            @Value("${alochat.kafka.retry-delay.long-ms}") long longDelayMs,
            @Value("${alochat.topics.normalized}") String normalizedTopic,
            @Value("${alochat.topics.retry-short}") String shortRetryTopic,
            @Value("${alochat.topics.retry-long}") String longRetryTopic
    ) {
        this.processingService = processingService;
        this.retryTopicPublisher = retryTopicPublisher;
        this.shortDelayMs = shortDelayMs;
        this.longDelayMs = longDelayMs;
        this.normalizedTopic = normalizedTopic;
        this.shortRetryTopic = shortRetryTopic;
        this.longRetryTopic = longRetryTopic;
    }

    public void processPrimary(MessageEnvelope envelope) {
        try {
            processingService.process(envelope);
        } catch (Exception exception) {
            log.warn("Processor primary flow failed messageId={} error={}", envelope.messageId(), exception.getMessage());
            retryTopicPublisher.publishShortRetry(envelope, normalizedTopic, TARGET_SERVICE, exception);
        }
    }

    public void processShortRetry(MessageEnvelope envelope, String targetService) {
        if (!TARGET_SERVICE.equals(targetService)) {
            return;
        }
        delay(shortDelayMs);
        try {
            processingService.processRetry(envelope);
        } catch (Exception exception) {
            log.warn("Processor short retry failed messageId={} error={}", envelope.messageId(), exception.getMessage());
            retryTopicPublisher.publishLongRetry(envelope, shortRetryTopic, TARGET_SERVICE, exception);
        }
    }

    public void processLongRetry(MessageEnvelope envelope, String targetService) {
        if (!TARGET_SERVICE.equals(targetService)) {
            return;
        }
        delay(longDelayMs);
        try {
            processingService.processRetry(envelope);
        } catch (Exception exception) {
            log.error("Processor long retry failed, sending to DLQ messageId={} error={}", envelope.messageId(), exception.getMessage());
            retryTopicPublisher.publishDlq(envelope, longRetryTopic, TARGET_SERVICE, exception);
        }
    }

    private void delay(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry delay interrupted", exception);
        }
    }
}
