package com.alochat.inbound.validation;

import com.alochat.contracts.message.Channel;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class WebPayloadValidator extends JsonPayloadValidatorSupport implements ChannelPayloadValidator {

    @Override
    public Channel channel() {
        return Channel.WEB;
    }

    @Override
    public void validate(JsonNode payload) {
        requireText(payload, "tenantId");
        requireText(payload, "messageId");
        requireText(payload, "conversationId");
        requireText(payload, "user", "id");
        requireText(payload, "user", "role");
        requireText(payload, "message", "text");
        requireText(payload, "context", "source");
        requireText(payload, "context", "locale");
        requireText(payload, "context", "client", "app");
        requireText(payload, "context", "client", "version");
        requireText(payload, "context", "session", "id");
    }
}
