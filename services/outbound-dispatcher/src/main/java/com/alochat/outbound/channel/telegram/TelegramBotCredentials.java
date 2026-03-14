package com.alochat.outbound.channel.telegram;

public record TelegramBotCredentials(
        String botToken,
        String botUsername,
        String apiBaseUrl
) {
}
