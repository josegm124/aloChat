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
import org.springframework.beans.factory.annotation.Value;

@Component
public class TelegramInboundAdapter implements ChannelInboundAdapter {

    private final MessageIdentityService identityService;
    private final String defaultTenantId;

    public TelegramInboundAdapter(
            MessageIdentityService identityService,
            @Value("${alochat.inbound.default-tenant-id:acme}") String defaultTenantId
    ) {
        this.identityService = identityService;
        this.defaultTenantId = defaultTenantId;
    }

    @Override
    public Channel channel() {
        return Channel.TELEGRAM;
    }

    @Override
    public MessageEnvelope adapt(JsonNode payload, InboundRequestContext context) {
        JsonNode message = payload.path("message");
        String externalMessageId = JsonNodeReader.text(message, "message_id");
        String conversationId = JsonNodeReader.text(message, "chat", "id");
        String userId = JsonNodeReader.text(message, "from", "id");
        String text = JsonNodeReader.text(message, "text");
        String languageCode = JsonNodeReader.nullToEmpty(JsonNodeReader.text(message, "from", "language_code"));
        String externalKey = identityService.externalKey(conversationId, externalMessageId);

        return new MessageEnvelope(
                identityService.newMessageId(),
                identityService.buildIdempotencyKey(Channel.TELEGRAM, externalKey, payload),
                context.traceId(),
                Channel.TELEGRAM,
                defaultTenantId,
                externalMessageId,
                conversationId,
                userId,
                context.receivedAt(),
                new NormalizedContent(ContentType.TEXT, text),
                null,
                Map.of(
                        "source", "telegram",
                        "updateId", JsonNodeReader.nullToEmpty(JsonNodeReader.text(payload, "update_id")),
                        "locale", languageCode,
                        "preferredLanguage", normalizeLanguage(languageCode)
                ),
                null,
                MessageStatus.NORMALIZED
        );
    }

    private String normalizeLanguage(String languageCode) {
        String normalized = languageCode == null ? "" : languageCode.trim().toLowerCase();
        if (normalized.startsWith("en")) {
            return "en";
        }
        return "es";
    }
}
