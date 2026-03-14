package com.alochat.inbound.service;

import com.alochat.contracts.message.Channel;
import com.alochat.contracts.message.MessageEnvelope;
import com.alochat.inbound.api.AcceptedMessageResponse;
import com.alochat.inbound.channel.ChannelInboundAdapter;
import com.alochat.inbound.model.InboundRequestContext;
import com.alochat.inbound.port.InboundMessageAuditRepository;
import com.alochat.inbound.publish.NormalizedMessagePublisher;
import com.alochat.inbound.validation.ChannelPayloadValidator;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class InboundMessageOrchestrator {

    private final Map<Channel, ChannelInboundAdapter> adapters = new EnumMap<>(Channel.class);
    private final Map<Channel, ChannelPayloadValidator> validators = new EnumMap<>(Channel.class);
    private final NormalizedMessagePublisher publisher;
    private final InboundMessageAuditRepository inboundMessageAuditRepository;

    public InboundMessageOrchestrator(
            List<ChannelInboundAdapter> channelAdapters,
            List<ChannelPayloadValidator> channelValidators,
            NormalizedMessagePublisher publisher,
            InboundMessageAuditRepository inboundMessageAuditRepository
    ) {
        for (ChannelInboundAdapter adapter : channelAdapters) {
            adapters.put(adapter.channel(), adapter);
        }
        for (ChannelPayloadValidator validator : channelValidators) {
            validators.put(validator.channel(), validator);
        }
        this.publisher = publisher;
        this.inboundMessageAuditRepository = inboundMessageAuditRepository;
    }

    public AcceptedMessageResponse handle(
            Channel channel,
            JsonNode payload,
            InboundRequestContext context
    ) {
        ChannelInboundAdapter adapter = adapters.get(channel);
        if (adapter == null) {
            throw new IllegalArgumentException("Unsupported channel: " + channel);
        }
        ChannelPayloadValidator validator = validators.get(channel);
        if (validator == null) {
            throw new IllegalArgumentException("Missing payload validator for channel: " + channel);
        }

        validator.validate(payload);
        MessageEnvelope normalizedMessage = adapter.adapt(payload, context);
        MessageEnvelope publishedMessage = publisher.publish(normalizedMessage);
        inboundMessageAuditRepository.save(publishedMessage);

        return new AcceptedMessageResponse(
                publishedMessage.messageId(),
                publishedMessage.idempotencyKey(),
                publishedMessage.channel(),
                publishedMessage.status()
        );
    }
}
