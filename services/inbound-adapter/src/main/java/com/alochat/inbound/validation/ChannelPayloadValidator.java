package com.alochat.inbound.validation;

import com.alochat.contracts.message.Channel;
import com.fasterxml.jackson.databind.JsonNode;

public interface ChannelPayloadValidator {

    Channel channel();

    void validate(JsonNode payload);
}
