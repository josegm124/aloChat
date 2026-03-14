package com.alochat.ai.service;

import com.alochat.ai.model.AiContext;
import com.alochat.ai.model.AiGenerationResult;
import com.alochat.ai.port.AiResponseGenerator;
import com.alochat.ai.port.CampaignHintRepository;
import com.alochat.ai.port.ConversationMemoryRepository;
import com.alochat.ai.port.MessageAuditRepository;
import com.alochat.ai.port.OutboundMessagePublisher;
import com.alochat.contracts.message.MessageEnvelope;
import com.alochat.contracts.message.MessageStatus;
import org.springframework.stereotype.Service;

@Service
public class AiOrchestrationService {

    private final AiResponseGenerator aiResponseGenerator;
    private final AiContextService aiContextService;
    private final MessageAuditRepository messageAuditRepository;
    private final ConversationMemoryRepository conversationMemoryRepository;
    private final CampaignHintRepository campaignHintRepository;
    private final OutboundMessagePublisher outboundMessagePublisher;

    public AiOrchestrationService(
            AiResponseGenerator aiResponseGenerator,
            AiContextService aiContextService,
            MessageAuditRepository messageAuditRepository,
            ConversationMemoryRepository conversationMemoryRepository,
            CampaignHintRepository campaignHintRepository,
            OutboundMessagePublisher outboundMessagePublisher
    ) {
        this.aiResponseGenerator = aiResponseGenerator;
        this.aiContextService = aiContextService;
        this.messageAuditRepository = messageAuditRepository;
        this.conversationMemoryRepository = conversationMemoryRepository;
        this.campaignHintRepository = campaignHintRepository;
        this.outboundMessagePublisher = outboundMessagePublisher;
    }

    public void process(MessageEnvelope envelope) {
        MessageEnvelope aiProcessing = envelope.withStatus(MessageStatus.AI_PROCESSING);
        messageAuditRepository.save(aiProcessing);
        AiContext aiContext = aiContextService.build(aiProcessing);
        AiGenerationResult generationResult = aiResponseGenerator.generate(aiProcessing, aiContext);
        conversationMemoryRepository.save(generationResult.conversationMemory());
        campaignHintRepository.save(generationResult.campaignHint());
        messageAuditRepository.save(generationResult.responseEnvelope());
        MessageEnvelope outboundEnvelope = outboundMessagePublisher.publish(
                generationResult.responseEnvelope().withStatus(MessageStatus.READY_FOR_DISPATCH)
        );
        messageAuditRepository.save(outboundEnvelope);
    }
}
