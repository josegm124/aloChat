package com.alochat.ai.service;

import com.alochat.contracts.message.MessageEnvelope;
import com.alochat.ai.port.RetryTopicPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiRetryOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AiRetryOrchestrator.class);
    private static final String TARGET_SERVICE = "ai-orchestrator";

    private final AiOrchestrationService aiOrchestrationService;
    private final RetryTopicPublisher retryTopicPublisher;
    private final long shortDelayMs;
    private final long longDelayMs;
    private final String aiTopic;
    private final String shortRetryTopic;
    private final String longRetryTopic;

    public AiRetryOrchestrator(
            AiOrchestrationService aiOrchestrationService,
            RetryTopicPublisher retryTopicPublisher,
            @Value("${alochat.kafka.retry-delay.short-ms}") long shortDelayMs,
            @Value("${alochat.kafka.retry-delay.long-ms}") long longDelayMs,
            @Value("${alochat.topics.ai}") String aiTopic,
            @Value("${alochat.topics.retry-short}") String shortRetryTopic,
            @Value("${alochat.topics.retry-long}") String longRetryTopic
    ) {
        this.aiOrchestrationService = aiOrchestrationService;
        this.retryTopicPublisher = retryTopicPublisher;
        this.shortDelayMs = shortDelayMs;
        this.longDelayMs = longDelayMs;
        this.aiTopic = aiTopic;
        this.shortRetryTopic = shortRetryTopic;
        this.longRetryTopic = longRetryTopic;
    }

    public void processPrimary(MessageEnvelope envelope) {
        try {
            aiOrchestrationService.process(envelope);
        } catch (Exception exception) {
            log.warn("AI primary flow failed messageId={} error={}", envelope.messageId(), exception.getMessage());
            retryTopicPublisher.publishShortRetry(envelope, aiTopic, TARGET_SERVICE, exception);
        }
    }

    public void processShortRetry(MessageEnvelope envelope, String targetService) {
        if (!TARGET_SERVICE.equals(targetService)) {
            return;
        }
        delay(shortDelayMs);
        try {
            aiOrchestrationService.process(envelope);
        } catch (Exception exception) {
            log.warn("AI short retry failed messageId={} error={}", envelope.messageId(), exception.getMessage());
            retryTopicPublisher.publishLongRetry(envelope, shortRetryTopic, TARGET_SERVICE, exception);
        }
    }

    public void processLongRetry(MessageEnvelope envelope, String targetService) {
        if (!TARGET_SERVICE.equals(targetService)) {
            return;
        }
        delay(longDelayMs);
        try {
            aiOrchestrationService.process(envelope);
        } catch (Exception exception) {
            log.error("AI long retry failed, sending to DLQ messageId={} error={}", envelope.messageId(), exception.getMessage());
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
