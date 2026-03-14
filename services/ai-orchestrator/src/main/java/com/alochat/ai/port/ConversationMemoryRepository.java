package com.alochat.ai.port;

import com.alochat.ai.model.ConversationMemory;
import java.util.Optional;

public interface ConversationMemoryRepository {

    Optional<ConversationMemory> findByMemoryKey(String memoryKey);

    void save(ConversationMemory conversationMemory);
}
