package com.alochat.inbound.port;

import com.alochat.contracts.message.MessageEnvelope;
import com.alochat.contracts.message.MessageStatus;
import com.alochat.inbound.api.MessageStatusResponse;
import java.util.List;
import java.util.Optional;

public interface InboundMessageAuditRepository {

    void save(MessageEnvelope envelope);

    Optional<MessageStatusResponse> findByMessageId(String messageId);

    List<MessageStatusResponse> findByConversationId(String conversationId, MessageStatus status, int limit);
}
