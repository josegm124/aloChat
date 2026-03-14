package com.alochat.ai.inbound;

import com.alochat.contracts.message.MessageEnvelope;
import com.alochat.ai.service.AiRetryOrchestrator;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class AiRetryConsumer {

    private final AiRetryOrchestrator retryOrchestrator;

    public AiRetryConsumer(AiRetryOrchestrator retryOrchestrator) {
        this.retryOrchestrator = retryOrchestrator;
    }

    @KafkaListener(
            topics = "${alochat.topics.ai}",
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
