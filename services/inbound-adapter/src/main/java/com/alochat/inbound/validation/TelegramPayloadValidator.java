package com.alochat.inbound.validation;

import com.alochat.contracts.message.Channel;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class TelegramPayloadValidator extends JsonPayloadValidatorSupport implements ChannelPayloadValidator {

    @Override
    public Channel channel() {
        return Channel.TELEGRAM;
    }

    @Override
    public void validate(JsonNode payload) {
        JsonNode message = payload.path("message");
        requireText(message, "message_id");
        requireText(message, "chat", "id");
        requireText(message, "from", "id");
        requireText(message, "text");
    }
}
