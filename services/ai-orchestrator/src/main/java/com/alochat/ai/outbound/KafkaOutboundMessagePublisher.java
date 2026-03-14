package com.alochat.ai.outbound;

import com.alochat.contracts.message.MessageEnvelope;
import com.alochat.contracts.message.MessageStatus;
import com.alochat.ai.port.OutboundMessagePublisher;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KafkaOutboundMessagePublisher implements OutboundMessagePublisher {

    private final KafkaTemplate<String, MessageEnvelope> kafkaTemplate;
    private final String outboundTopic;

    public KafkaOutboundMessagePublisher(
            KafkaTemplate<String, MessageEnvelope> kafkaTemplate,
            @Value("${alochat.topics.outbound}") String outboundTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.outboundTopic = outboundTopic;
    }

    @Override
    public MessageEnvelope publish(MessageEnvelope envelope) {
        String key = envelope.conversationId() == null || envelope.conversationId().isBlank()
                ? envelope.messageId()
                : envelope.conversationId();
        ProducerRecord<String, MessageEnvelope> record = new ProducerRecord<>(outboundTopic, key, envelope);
        record.headers().add(new RecordHeader("messageId", envelope.messageId().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("traceId", envelope.traceId().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("channel", envelope.channel().name().getBytes(StandardCharsets.UTF_8)));
        kafkaTemplate.send(record);
        return envelope.withStatus(MessageStatus.READY_FOR_DISPATCH);
    }
}
