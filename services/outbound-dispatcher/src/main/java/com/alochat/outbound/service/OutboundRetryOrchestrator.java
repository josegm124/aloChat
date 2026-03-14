package com.alochat.outbound.service;

import com.alochat.contracts.message.MessageEnvelope;
import com.alochat.outbound.port.RetryTopicPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OutboundRetryOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OutboundRetryOrchestrator.class);
    private static final String TARGET_SERVICE = "outbound-dispatcher";

    private final OutboundDispatchService outboundDispatchService;
    private final RetryTopicPublisher retryTopicPublisher;
    private final long shortDelayMs;
    private final long longDelayMs;
    private final String outboundTopic;
    private final String shortRetryTopic;
    private final String longRetryTopic;

    public OutboundRetryOrchestrator(
            OutboundDispatchService outboundDispatchService,
            RetryTopicPublisher retryTopicPublisher,
            @Value("${alochat.kafka.retry-delay.short-ms}") long shortDelayMs,
            @Value("${alochat.kafka.retry-delay.long-ms}") long longDelayMs,
            @Value("${alochat.topics.outbound}") String outboundTopic,
            @Value("${alochat.topics.retry-short}") String shortRetryTopic,
            @Value("${alochat.topics.retry-long}") String longRetryTopic
    ) {
        this.outboundDispatchService = outboundDispatchService;
        this.retryTopicPublisher = retryTopicPublisher;
        this.shortDelayMs = shortDelayMs;
        this.longDelayMs = longDelayMs;
        this.outboundTopic = outboundTopic;
        this.shortRetryTopic = shortRetryTopic;
        this.longRetryTopic = longRetryTopic;
    }

    public void processPrimary(MessageEnvelope envelope) {
        try {
            outboundDispatchService.dispatch(envelope);
        } catch (Exception exception) {
            log.warn("Outbound primary flow failed messageId={} error={}", envelope.messageId(), exception.getMessage());
            retryTopicPublisher.publishShortRetry(envelope, outboundTopic, TARGET_SERVICE, exception);
        }
    }

    public void processShortRetry(MessageEnvelope envelope, String targetService) {
        if (!TARGET_SERVICE.equals(targetService)) {
            return;
        }
        delay(shortDelayMs);
        try {
            outboundDispatchService.dispatch(envelope);
        } catch (Exception exception) {
            log.warn("Outbound short retry failed messageId={} error={}", envelope.messageId(), exception.getMessage());
            retryTopicPublisher.publishLongRetry(envelope, shortRetryTopic, TARGET_SERVICE, exception);
        }
    }

    public void processLongRetry(MessageEnvelope envelope, String targetService) {
        if (!TARGET_SERVICE.equals(targetService)) {
            return;
        }
        delay(longDelayMs);
        try {
            outboundDispatchService.dispatch(envelope);
        } catch (Exception exception) {
            log.error("Outbound long retry failed, sending to DLQ messageId={} error={}", envelope.messageId(), exception.getMessage());
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
