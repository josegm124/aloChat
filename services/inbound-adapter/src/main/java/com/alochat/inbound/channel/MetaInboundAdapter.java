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
public class MetaInboundAdapter implements ChannelInboundAdapter {

    private final MessageIdentityService identityService;

    public MetaInboundAdapter(MessageIdentityService identityService) {
        this.identityService = identityService;
    }

    @Override
    public Channel channel() {
        return Channel.META;
    }

    @Override
    public MessageEnvelope adapt(JsonNode payload, InboundRequestContext context) {
        JsonNode entry = JsonNodeReader.firstArrayElement(payload, "entry");
        JsonNode change = JsonNodeReader.firstArrayElement(entry, "changes");
        JsonNode value = change.path("value");
        JsonNode message = JsonNodeReader.firstArrayElement(value, "messages");
        JsonNode contact = JsonNodeReader.firstArrayElement(value, "contacts");

        String externalMessageId = JsonNodeReader.text(message, "id");
        String userId = JsonNodeReader.text(message, "from");
        String conversationId = JsonNodeReader.firstNonBlank(
                JsonNodeReader.text(contact, "wa_id"),
                userId
        );
        String text = JsonNodeReader.text(message, "text", "body");
        String tenantId = JsonNodeReader.firstNonBlank(
                JsonNodeReader.text(value, "metadata", "phone_number_id"),
                "default"
        );
        String externalKey = identityService.externalKey(
                tenantId,
                externalMessageId
        );

        return new MessageEnvelope(
                identityService.newMessageId(),
                identityService.buildIdempotencyKey(Channel.META, externalKey, payload),
                context.traceId(),
                Channel.META,
                tenantId,
                externalMessageId,
                conversationId,
                userId,
                context.receivedAt(),
                new NormalizedContent(ContentType.TEXT, text),
                null,
                Map.of(
                        "source", "meta",
                        "displayPhoneNumber", JsonNodeReader.nullToEmpty(JsonNodeReader.text(value, "metadata", "display_phone_number"))
                ),
                null,
                MessageStatus.NORMALIZED
        );
    }
}
