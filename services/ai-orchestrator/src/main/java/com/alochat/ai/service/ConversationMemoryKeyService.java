package com.alochat.ai.service;

import com.alochat.contracts.message.MessageEnvelope;
import org.springframework.stereotype.Service;

@Service
public class ConversationMemoryKeyService {

    public String build(MessageEnvelope envelope) {
        String tenantId = defaultValue(envelope.tenantId(), "default");
        String channel = envelope.channel().name().toLowerCase();
        String userId = defaultValue(envelope.userId(), defaultValue(envelope.conversationId(), envelope.messageId()));
        String conversationId = defaultValue(envelope.conversationId(), userId);
        return tenantId + ":" + channel + ":" + userId + ":" + conversationId;
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
