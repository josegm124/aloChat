package com.alochat.processor.port;

import com.alochat.contracts.message.MessageEnvelope;

public interface IdempotencyRepository {

    boolean register(MessageEnvelope envelope);
}
