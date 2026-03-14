package com.alochat.outbound.channel.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TelegramSendMessageRequest(
        @JsonProperty("chat_id") String chatId,
        String text
) {
}
