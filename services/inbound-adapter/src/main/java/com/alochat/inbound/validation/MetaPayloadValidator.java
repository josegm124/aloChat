package com.alochat.inbound.validation;

import com.alochat.contracts.message.Channel;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class MetaPayloadValidator extends JsonPayloadValidatorSupport implements ChannelPayloadValidator {

    @Override
    public Channel channel() {
        return Channel.META;
    }

    @Override
    public void validate(JsonNode payload) {
        JsonNode entry = requireFirstArrayElement(payload, "entry");
        JsonNode change = requireFirstArrayElement(entry, "changes");
        JsonNode value = change.path("value");
        JsonNode message = requireFirstArrayElement(value, "messages");

        requireText(message, "id");
        requireText(message, "from");
        requireText(message, "text", "body");
        requireText(value, "metadata", "phone_number_id");
    }
}
