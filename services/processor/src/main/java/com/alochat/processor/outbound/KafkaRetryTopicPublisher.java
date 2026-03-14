package com.alochat.processor.outbound;

import com.alochat.contracts.message.MessageEnvelope;
import com.alochat.contracts.message.RetryHeaders;
import com.alochat.processor.port.RetryTopicPublisher;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KafkaRetryTopicPublisher implements RetryTopicPublisher {

    private final KafkaTemplate<String, MessageEnvelope> kafkaTemplate;
    private final String shortRetryTopic;
    private final String longRetryTopic;
    private final String dlqTopic;

    public KafkaRetryTopicPublisher(
            KafkaTemplate<String, MessageEnvelope> kafkaTemplate,
            @Value("${alochat.topics.retry-short}") String shortRetryTopic,
            @Value("${alochat.topics.retry-long}") String longRetryTopic,
            @Value("${alochat.topics.dlq}") String dlqTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.shortRetryTopic = shortRetryTopic;
        this.longRetryTopic = longRetryTopic;
        this.dlqTopic = dlqTopic;
    }

    @Override
    public void publishShortRetry(MessageEnvelope envelope, String sourceTopic, String targetService, Throwable throwable) {
        send(shortRetryTopic, envelope, sourceTopic, targetService, "short", "1", throwable);
    }

    @Override
    public void publishLongRetry(MessageEnvelope envelope, String sourceTopic, String targetService, Throwable throwable) {
        send(longRetryTopic, envelope, sourceTopic, targetService, "long", "2", throwable);
    }

    @Override
    public void publishDlq(MessageEnvelope envelope, String sourceTopic, String targetService, Throwable throwable) {
        send(dlqTopic, envelope, sourceTopic, targetService, "dlq", "3", throwable);
    }

    private void send(
            String topic,
            MessageEnvelope envelope,
            String sourceTopic,
            String targetService,
            String retryStage,
            String retryCount,
            Throwable throwable
    ) {
        String key = envelope.conversationId() == null || envelope.conversationId().isBlank()
                ? envelope.messageId()
                : envelope.conversationId();
        ProducerRecord<String, MessageEnvelope> record = new ProducerRecord<>(topic, key, envelope);
        record.headers().add(new RecordHeader(RetryHeaders.TARGET_SERVICE, targetService.getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader(RetryHeaders.SOURCE_TOPIC, sourceTopic.getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader(RetryHeaders.RETRY_STAGE, retryStage.getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader(RetryHeaders.RETRY_COUNT, retryCount.getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader(
                RetryHeaders.FAILURE_REASON,
                throwable.getMessage() == null ? new byte[0] : throwable.getMessage().getBytes(StandardCharsets.UTF_8)
        ));
        kafkaTemplate.send(record);
    }
}
