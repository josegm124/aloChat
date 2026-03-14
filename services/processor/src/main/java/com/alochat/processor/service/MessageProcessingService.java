package com.alochat.processor.service;

import com.alochat.contracts.message.MessageEnvelope;
import com.alochat.contracts.message.MessageStatus;
import com.alochat.processor.port.AiMessagePublisher;
import com.alochat.processor.port.ConversationStateRepository;
import com.alochat.processor.port.IdempotencyRepository;
import com.alochat.processor.port.MessageAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MessageProcessingService {

    private static final Logger log = LoggerFactory.getLogger(MessageProcessingService.class);

    private final IdempotencyRepository idempotencyRepository;
    private final ConversationStateRepository conversationStateRepository;
    private final MessageAuditRepository messageAuditRepository;
    private final AiMessagePublisher aiMessagePublisher;

    public MessageProcessingService(
            IdempotencyRepository idempotencyRepository,
            ConversationStateRepository conversationStateRepository,
            MessageAuditRepository messageAuditRepository,
            AiMessagePublisher aiMessagePublisher
    ) {
        this.idempotencyRepository = idempotencyRepository;
        this.conversationStateRepository = conversationStateRepository;
        this.messageAuditRepository = messageAuditRepository;
        this.aiMessagePublisher = aiMessagePublisher;
    }

    public void process(MessageEnvelope envelope) {
        if (!idempotencyRepository.register(envelope)) {
            log.info(
                    "Skipping duplicate message messageId={} idempotencyKey={}",
                    envelope.messageId(),
                    envelope.idempotencyKey()
            );
            return;
        }

        processInternal(envelope);
    }

    public void processRetry(MessageEnvelope envelope) {
        processInternal(envelope);
    }

    private void processInternal(MessageEnvelope envelope) {
        MessageEnvelope processingEnvelope = envelope.withStatus(MessageStatus.PROCESSING);
        conversationStateRepository.store(processingEnvelope);
        messageAuditRepository.save(processingEnvelope);
        MessageEnvelope aiEnvelope = aiMessagePublisher.publish(
                processingEnvelope.withStatus(MessageStatus.AI_PROCESSING)
        );
        messageAuditRepository.save(aiEnvelope);

        log.info(
                "Processor published ai message messageId={} conversationId={} channel={}",
                aiEnvelope.messageId(),
                aiEnvelope.conversationId(),
                aiEnvelope.channel()
        );
    }
}
