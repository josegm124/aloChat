package com.alochat.processor.port;

import com.alochat.contracts.message.MessageEnvelope;

public interface MessageAuditRepository {

    void save(MessageEnvelope envelope);
}
