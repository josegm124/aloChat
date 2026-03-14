package com.alochat.outbound.service;

import com.alochat.contracts.message.Channel;
import com.alochat.contracts.message.MessageEnvelope;
import com.alochat.contracts.message.MessageStatus;
import com.alochat.outbound.channel.ChannelDispatcher;
import com.alochat.outbound.port.ConversationStateCleanupRepository;
import com.alochat.outbound.port.FinalMessageAuditRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OutboundDispatchService {

    private final Map<Channel, ChannelDispatcher> dispatchers = new EnumMap<>(Channel.class);
    private final FinalMessageAuditRepository finalMessageAuditRepository;
    private final ConversationStateCleanupRepository conversationStateCleanupRepository;

    public OutboundDispatchService(
            List<ChannelDispatcher> channelDispatchers,
            FinalMessageAuditRepository finalMessageAuditRepository,
            ConversationStateCleanupRepository conversationStateCleanupRepository
    ) {
        for (ChannelDispatcher dispatcher : channelDispatchers) {
            dispatchers.put(dispatcher.channel(), dispatcher);
        }
        this.finalMessageAuditRepository = finalMessageAuditRepository;
        this.conversationStateCleanupRepository = conversationStateCleanupRepository;
    }

    public void dispatch(MessageEnvelope envelope) {
        ChannelDispatcher dispatcher = dispatchers.get(envelope.channel());
        if (dispatcher == null) {
            throw new IllegalArgumentException("Unsupported outbound channel: " + envelope.channel());
        }

        dispatcher.dispatch(envelope);
        MessageEnvelope dispatchedEnvelope = envelope.withStatus(MessageStatus.DISPATCHED);
        finalMessageAuditRepository.save(dispatchedEnvelope);
        conversationStateCleanupRepository.clear(dispatchedEnvelope);
    }
}
