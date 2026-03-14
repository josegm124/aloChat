package com.alochat.ai.model;

import java.util.Map;

public record KnowledgeSnippet(
        String snippetId,
        String title,
        String excerpt,
        String sourceType,
        String sourceRef,
        double score,
        Map<String, String> metadata
) {
}
