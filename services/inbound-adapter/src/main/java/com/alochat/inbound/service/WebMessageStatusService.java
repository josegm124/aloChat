package com.alochat.inbound.service;

import com.alochat.contracts.message.MessageStatus;
import com.alochat.inbound.api.MessageStatusResponse;
import java.util.List;
import com.alochat.inbound.port.InboundMessageAuditRepository;
import org.springframework.stereotype.Service;

@Service
public class WebMessageStatusService {

    private final InboundMessageAuditRepository inboundMessageAuditRepository;

    public WebMessageStatusService(InboundMessageAuditRepository inboundMessageAuditRepository) {
        this.inboundMessageAuditRepository = inboundMessageAuditRepository;
    }

    public MessageStatusResponse findByMessageId(String messageId) {
        return inboundMessageAuditRepository.findByMessageId(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));
    }

    public List<MessageStatusResponse> findConversationMessages(String conversationId, int limit) {
        return inboundMessageAuditRepository.findByConversationId(conversationId, MessageStatus.DISPATCHED, limit);
    }
}
