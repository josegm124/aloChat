package com.alochat.outbound.channel;

import com.alochat.contracts.message.Channel;
import com.alochat.contracts.message.MessageEnvelope;

public interface ChannelDispatcher {

    Channel channel();

    void dispatch(MessageEnvelope envelope);
}
