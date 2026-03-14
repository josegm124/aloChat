package com.alochat.ai.port;

import com.alochat.contracts.message.MessageEnvelope;

public interface OutboundMessagePublisher {

    MessageEnvelope publish(MessageEnvelope envelope);
}
