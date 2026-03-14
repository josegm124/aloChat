package com.alochat.processor.port;

import com.alochat.contracts.message.MessageEnvelope;

public interface AiMessagePublisher {

    MessageEnvelope publish(MessageEnvelope envelope);
}
