package com.alochat.inbound.publish;

import com.alochat.contracts.message.MessageEnvelope;
import com.alochat.contracts.message.MessageStatus;
import com.alochat.inbound.service.MessageIdentityService;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaNormalizedMessagePublisher implements NormalizedMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaNormalizedMessagePublisher.class);

    private final String normalizedTopic;
    private final Duration publishTimeout;
    private final KafkaTemplate<String, MessageEnvelope> kafkaTemplate;
    private final MessageIdentityService identityService;

    public KafkaNormalizedMessagePublisher(
            @Value("${alochat.topics.normalized}") String normalizedTopic,
            @Value("${alochat.kafka.publish-timeout}") Duration publishTimeout,
            KafkaTemplate<String, MessageEnvelope> kafkaTemplate,
            MessageIdentityService identityService
    ) {
        this.normalizedTopic = normalizedTopic;
        this.publishTimeout = publishTimeout;
        this.kafkaTemplate = kafkaTemplate;
        this.identityService = identityService;
    }

    @Override
    public MessageEnvelope publish(MessageEnvelope envelope) {
        String key = identityService.partitionKey(envelope.conversationId(), envelope.messageId());
        ProducerRecord<String, MessageEnvelope> record = new ProducerRecord<>(normalizedTopic, key, envelope);
        record.headers().add(new RecordHeader("messageId", envelope.messageId().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("traceId", envelope.traceId().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("channel", envelope.channel().name().getBytes(StandardCharsets.UTF_8)));

        try {
            kafkaTemplate.send(record).get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to publish normalized message to Kafka", exception);
        }

        log.info(
                "Published normalized message to topic={} messageId={} channel={} conversationId={}",
                normalizedTopic,
                envelope.messageId(),
                envelope.channel(),
                envelope.conversationId()
        );
        return envelope.withStatus(MessageStatus.PUBLISHED);
    }
}
