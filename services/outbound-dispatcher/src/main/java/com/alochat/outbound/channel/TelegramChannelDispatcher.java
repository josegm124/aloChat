package com.alochat.outbound.channel;

import com.alochat.contracts.message.Channel;
import com.alochat.contracts.message.MessageEnvelope;
import com.alochat.outbound.channel.telegram.TelegramDispatchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TelegramChannelDispatcher implements ChannelDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TelegramChannelDispatcher.class);
    private final TelegramDispatchClient telegramDispatchClient;

    public TelegramChannelDispatcher(TelegramDispatchClient telegramDispatchClient) {
        this.telegramDispatchClient = telegramDispatchClient;
    }

    @Override
    public Channel channel() {
        return Channel.TELEGRAM;
    }

    @Override
    public void dispatch(MessageEnvelope envelope) {
        telegramDispatchClient.sendMessage(envelope);
        log.info("Dispatched outbound Telegram messageId={} userId={}", envelope.messageId(), envelope.userId());
    }
}
