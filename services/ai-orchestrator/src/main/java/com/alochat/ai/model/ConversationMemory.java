package com.alochat.ai.model;

import java.time.Instant;
import java.util.List;

public record ConversationMemory(
        String memoryKey,
        String tenantId,
        String channel,
        String conversationId,
        String userId,
        String summary,
        String lastQuestion,
        List<String> trackedProducts,
        List<String> interestTags,
        Instant lastInteractionAt,
        Instant followUpAt,
        String followUpReason
) {
    public ConversationMemory {
        trackedProducts = trackedProducts == null ? List.of() : List.copyOf(trackedProducts);
        interestTags = interestTags == null ? List.of() : List.copyOf(interestTags);
    }
}
