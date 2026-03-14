package com.alochat.outbound.port;

import com.alochat.contracts.message.MessageEnvelope;

public interface ConversationStateCleanupRepository {

    void clear(MessageEnvelope envelope);
}
