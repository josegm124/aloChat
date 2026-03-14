package com.alochat.inbound.channel;

import com.alochat.contracts.message.Channel;
import com.alochat.contracts.message.MessageEnvelope;
import com.alochat.inbound.model.InboundRequestContext;
import com.fasterxml.jackson.databind.JsonNode;

public interface ChannelInboundAdapter {

    Channel channel();

    MessageEnvelope adapt(JsonNode payload, InboundRequestContext context);
}
