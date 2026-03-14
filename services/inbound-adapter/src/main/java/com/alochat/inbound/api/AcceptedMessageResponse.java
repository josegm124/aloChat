package com.alochat.inbound.api;

import com.alochat.contracts.message.Channel;
import com.alochat.contracts.message.MessageStatus;

public record AcceptedMessageResponse(
        String messageId,
        String idempotencyKey,
        Channel channel,
        MessageStatus status
) {
}
