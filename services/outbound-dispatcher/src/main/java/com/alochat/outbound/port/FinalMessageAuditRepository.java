package com.alochat.outbound.port;

import com.alochat.contracts.message.MessageEnvelope;

public interface FinalMessageAuditRepository {

    void save(MessageEnvelope envelope);
}
