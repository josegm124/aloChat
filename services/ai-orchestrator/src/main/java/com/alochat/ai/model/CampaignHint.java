package com.alochat.ai.model;

import java.time.Instant;
import java.util.List;

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
        Instant createdAt,
        Instant triggerAt
) {
    public CampaignHint {
        relatedProducts = relatedProducts == null ? List.of() : List.copyOf(relatedProducts);
    }
}
