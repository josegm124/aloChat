package com.alochat.inbound.api;

import com.alochat.contracts.message.Channel;
import com.alochat.contracts.message.MessageStatus;
import java.time.Instant;

public record MessageStatusResponse(
        String messageId,
        String conversationId,
        Channel channel,
        MessageStatus status,
        Instant updatedAt,
        String contentText
) {
}
