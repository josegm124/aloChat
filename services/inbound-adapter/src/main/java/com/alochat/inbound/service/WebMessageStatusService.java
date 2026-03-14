package com.alochat.inbound.service;

import com.alochat.inbound.api.MessageStatusResponse;
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
}
