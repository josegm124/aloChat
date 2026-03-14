package com.alochat.processor.inbound;

import com.alochat.contracts.message.MessageEnvelope;
import com.alochat.processor.service.MessageRetryOrchestrator;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class ProcessorRetryConsumer {

    private final MessageRetryOrchestrator retryOrchestrator;

    public ProcessorRetryConsumer(MessageRetryOrchestrator retryOrchestrator) {
        this.retryOrchestrator = retryOrchestrator;
    }

    @KafkaListener(
            topics = "${alochat.topics.normalized}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onPrimary(MessageEnvelope envelope) {
        retryOrchestrator.processPrimary(envelope);
    }

    @KafkaListener(
            topics = "${alochat.topics.retry-short}",
            groupId = "${alochat.kafka.retry-groups.short}"
    )
    public void onShortRetry(
            MessageEnvelope envelope,
            @Header(name = "x-target-service", required = false) String targetService
    ) {
        retryOrchestrator.processShortRetry(envelope, targetService);
    }

    @KafkaListener(
            topics = "${alochat.topics.retry-long}",
            groupId = "${alochat.kafka.retry-groups.long}"
    )
    public void onLongRetry(
            MessageEnvelope envelope,
            @Header(name = "x-target-service", required = false) String targetService
    ) {
        retryOrchestrator.processLongRetry(envelope, targetService);
    }
}
