package com.alochat.inbound.publish;

import com.alochat.contracts.message.MessageEnvelope;

public interface NormalizedMessagePublisher {

    MessageEnvelope publish(MessageEnvelope envelope);
}
