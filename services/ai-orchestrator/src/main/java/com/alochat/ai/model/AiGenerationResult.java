package com.alochat.ai.model;

import com.alochat.contracts.message.MessageEnvelope;

public record AiGenerationResult(
        MessageEnvelope responseEnvelope,
        ConversationMemory conversationMemory,
        CampaignHint campaignHint
) {
}
