package com.alo.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "alo.search")
public record SearchProperties(
        boolean enabled,
        String endpoint,
        String region,
        String corpusIndex,
        int maxResults,
        int lexicalCandidates,
        int vectorCandidates,
        double lexicalWeight,
        double vectorWeight,
        boolean embeddingsEnabled,
        String embeddingModelId,
        int embeddingDimensions,
        boolean normalizeEmbeddings,
        String bedrockRegion
) {
}
