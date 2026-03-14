package com.alochat.processor.port;

import com.alochat.contracts.message.MessageEnvelope;

public interface ConversationStateRepository {

    void store(MessageEnvelope envelope);
}
