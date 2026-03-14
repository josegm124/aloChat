package com.alochat.ai.service;

import com.alochat.ai.model.AiContext;
import com.alochat.ai.model.ConversationMemory;
import com.alochat.ai.port.ConversationMemoryRepository;
import com.alochat.ai.port.KnowledgeRetriever;
import com.alochat.contracts.message.MessageEnvelope;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiContextService {

    private final ConversationMemoryRepository conversationMemoryRepository;
    private final KnowledgeRetriever knowledgeRetriever;
    private final ConversationMemoryKeyService conversationMemoryKeyService;
    private final int snippetLimit;

    public AiContextService(
            ConversationMemoryRepository conversationMemoryRepository,
            KnowledgeRetriever knowledgeRetriever,
            ConversationMemoryKeyService conversationMemoryKeyService,
            @Value("${alochat.ai.knowledge.snippet-limit}") int snippetLimit
    ) {
        this.conversationMemoryRepository = conversationMemoryRepository;
        this.knowledgeRetriever = knowledgeRetriever;
        this.conversationMemoryKeyService = conversationMemoryKeyService;
        this.snippetLimit = snippetLimit;
    }

    public AiContext build(MessageEnvelope envelope) {
        String memoryKey = conversationMemoryKeyService.build(envelope);
        Optional<ConversationMemory> memory = conversationMemoryRepository.findByMemoryKey(memoryKey);
        List<com.alochat.ai.model.KnowledgeSnippet> snippets = knowledgeRetriever.retrieve(envelope, snippetLimit);
        return new AiContext(memory.orElse(null), snippets);
    }
}
