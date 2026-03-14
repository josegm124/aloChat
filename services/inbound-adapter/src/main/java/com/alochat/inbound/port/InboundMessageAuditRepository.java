package com.alochat.inbound.port;

import com.alochat.contracts.message.MessageEnvelope;
import com.alochat.inbound.api.MessageStatusResponse;
import java.util.Optional;

public interface InboundMessageAuditRepository {

    void save(MessageEnvelope envelope);

    Optional<MessageStatusResponse> findByMessageId(String messageId);
}
