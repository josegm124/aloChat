package com.alochat.inbound.model;

import com.alochat.contracts.message.Channel;
import java.time.Instant;
import java.util.Map;

public record InboundRequestContext(
        Channel channel,
        Instant receivedAt,
        String traceId,
        String remoteAddress,
        String externalRequestId,
        Map<String, String> headers
) {
    public InboundRequestContext {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
