package com.alo.retry.service;

import com.alo.contracts.events.KafkaTopics;
import com.alo.support.kafka.KafkaResilienceProperties;
import com.alo.support.kafka.ResilientKafkaPublisher;
import com.alo.support.kafka.RetryTopicHeaders;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Service;

@Service
public class RetryRepublisherService {
    private static final Logger log = LoggerFactory.getLogger(RetryRepublisherService.class);

    private final ResilientKafkaPublisher resilientKafkaPublisher;
    private final KafkaResilienceProperties properties;

    public RetryRepublisherService(
            ResilientKafkaPublisher resilientKafkaPublisher,
            KafkaResilienceProperties properties
    ) {
        this.resilientKafkaPublisher = resilientKafkaPublisher;
        this.properties = properties;
    }

    public void republishShort(ConsumerRecord<String, Object> record) {
        republish(record, properties.getRetryRouter().getShortDelayMs(), KafkaTopics.RETRY_SHORT);
    }

    public void republishLong(ConsumerRecord<String, Object> record) {
        republish(record, properties.getRetryRouter().getLongDelayMs(), KafkaTopics.RETRY_LONG);
    }

    private void republish(ConsumerRecord<String, Object> record, long delayMs, String retryTopic) {
        String originalTopic = headerValue(record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC));
        if (originalTopic == null || originalTopic.isBlank()) {
            throw new IllegalStateException("Missing original topic header on retry record topic=" + retryTopic);
        }

        sleep(delayMs);

        ProducerRecord<Object, Object> producerRecord = new ProducerRecord<>(
                originalTopic,
                null,
                record.key(),
                record.value(),
                retryHeaders(record.headers())
        );
        resilientKafkaPublisher.send(producerRecord);
        log.info(
                "retry republished retryTopic={} originalTopic={} key={} attempt={} stage={}",
                retryTopic,
                originalTopic,
                record.key(),
                RetryTopicHeaders.retryAttempt(record.headers()),
                RetryTopicHeaders.retryStage(record.headers())
        );
    }

    private Headers retryHeaders(Headers sourceHeaders) {
        RecordHeaders target = new RecordHeaders();
        copyHeader(sourceHeaders, target, RetryTopicHeaders.RETRY_STAGE);
        copyHeader(sourceHeaders, target, RetryTopicHeaders.RETRY_ATTEMPT);
        return target;
    }

    private void copyHeader(Headers source, RecordHeaders target, String headerName) {
        Header header = source.lastHeader(headerName);
        if (header != null && header.value() != null) {
            target.add(headerName, header.value());
        }
    }

    private String headerValue(Header header) {
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    private void sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry delay interrupted", exception);
        }
    }
}
