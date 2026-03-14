package com.alochat.outbound.channel;

import com.alochat.contracts.message.Channel;
import com.alochat.contracts.message.MessageEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MetaChannelDispatcher implements ChannelDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MetaChannelDispatcher.class);

    @Override
    public Channel channel() {
        return Channel.META;
    }

    @Override
    public void dispatch(MessageEnvelope envelope) {
        log.info("Dispatching outbound placeholder to Meta messageId={} userId={}", envelope.messageId(), envelope.userId());
    }
}
