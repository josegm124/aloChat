package com.alochat.inbound.channel;

import com.alochat.contracts.message.Channel;
import com.alochat.contracts.message.ContentType;
import com.alochat.contracts.message.MessageEnvelope;
import com.alochat.contracts.message.MessageStatus;
import com.alochat.contracts.message.NormalizedContent;
import com.alochat.inbound.model.InboundRequestContext;
import com.alochat.inbound.service.MessageIdentityService;
import com.alochat.inbound.util.JsonNodeReader;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WebInboundAdapter implements ChannelInboundAdapter {

    private final MessageIdentityService identityService;

    public WebInboundAdapter(MessageIdentityService identityService) {
        this.identityService = identityService;
    }

    @Override
    public Channel channel() {
        return Channel.WEB;
    }

    @Override
    public MessageEnvelope adapt(JsonNode payload, InboundRequestContext context) {
        String externalMessageId = JsonNodeReader.text(payload, "messageId");
        String conversationId = JsonNodeReader.text(payload, "conversationId");
        String userId = JsonNodeReader.text(payload, "user", "id");
        String tenantId = JsonNodeReader.firstNonBlank(JsonNodeReader.text(payload, "tenantId"), "default");
        String text = JsonNodeReader.text(payload, "message", "text");
        String externalKey = identityService.externalKey(tenantId, externalMessageId);

        return new MessageEnvelope(
                identityService.newMessageId(),
                identityService.buildIdempotencyKey(Channel.WEB, externalKey, payload),
                context.traceId(),
                Channel.WEB,
                tenantId,
                externalMessageId,
                conversationId,
                userId,
                context.receivedAt(),
                new NormalizedContent(ContentType.TEXT, text),
                null,
                Map.of(
                        "source", "web",
                        "sessionId", JsonNodeReader.nullToEmpty(JsonNodeReader.text(payload, "context", "session", "id")),
                        "userRole", JsonNodeReader.nullToEmpty(JsonNodeReader.text(payload, "user", "role")),
                        "locale", JsonNodeReader.nullToEmpty(JsonNodeReader.text(payload, "context", "locale")),
                        "clientApp", JsonNodeReader.nullToEmpty(JsonNodeReader.text(payload, "context", "client", "app"))
                ),
                null,
                MessageStatus.NORMALIZED
        );
    }
}
