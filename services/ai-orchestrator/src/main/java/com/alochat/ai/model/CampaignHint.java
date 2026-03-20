package com.alochat.ai.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CampaignHint(
        String hintId,
        String memoryKey,
        String tenantId,
        String channel,
        String conversationId,
        String userId,
        String hintType,
        String reason,
        List<String> relatedProducts,
        Map<String, String> relatedProductPrices,
        Instant createdAt,
        Instant triggerAt,
        Instant lastTriggeredAt
) {
    public CampaignHint {
        relatedProducts = relatedProducts == null ? List.of() : List.copyOf(relatedProducts);
        relatedProductPrices = relatedProductPrices == null ? Map.of() : Map.copyOf(relatedProductPrices);
    }
}
