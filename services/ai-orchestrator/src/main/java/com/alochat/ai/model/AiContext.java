package com.alochat.ai.model;

import java.util.List;

public record AiContext(
        ConversationMemory conversationMemory,
        List<KnowledgeSnippet> knowledgeSnippets
) {
    public AiContext {
        knowledgeSnippets = knowledgeSnippets == null ? List.of() : List.copyOf(knowledgeSnippets);
    }
}
