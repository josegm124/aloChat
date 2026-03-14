package com.alochat.processor.outbound;

import com.alochat.contracts.message.MessageEnvelope;
import com.alochat.contracts.message.MessageStatus;
import com.alochat.processor.port.AiMessagePublisher;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KafkaAiMessagePublisher implements AiMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaAiMessagePublisher.class);

    private final KafkaTemplate<String, MessageEnvelope> kafkaTemplate;
    private final String aiTopic;

    public KafkaAiMessagePublisher(
            KafkaTemplate<String, MessageEnvelope> kafkaTemplate,
            @Value("${alochat.topics.ai}") String aiTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.aiTopic = aiTopic;
    }

    @Override
    public MessageEnvelope publish(MessageEnvelope envelope) {
        String key = envelope.conversationId() == null || envelope.conversationId().isBlank()
                ? envelope.messageId()
                : envelope.conversationId();

        ProducerRecord<String, MessageEnvelope> record = new ProducerRecord<>(aiTopic, key, envelope);
        record.headers().add(new RecordHeader("messageId", envelope.messageId().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("traceId", envelope.traceId().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("channel", envelope.channel().name().getBytes(StandardCharsets.UTF_8)));

        kafkaTemplate.send(record);
        log.info(
                "Published message to ai topic={} messageId={} channel={}",
                aiTopic,
                envelope.messageId(),
                envelope.channel()
        );
        return envelope.withStatus(MessageStatus.AI_PROCESSING);
    }
}
