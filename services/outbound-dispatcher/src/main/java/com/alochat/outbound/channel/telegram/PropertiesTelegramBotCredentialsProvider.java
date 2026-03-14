package com.alochat.outbound.channel.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PropertiesTelegramBotCredentialsProvider implements TelegramBotCredentialsProvider {

    private final TelegramBotCredentials telegramBotCredentials;

    public PropertiesTelegramBotCredentialsProvider(
            @Value("${alochat.telegram.bot-token:}") String botToken,
            @Value("${alochat.telegram.bot-username:AloChat23Bot}") String botUsername,
            @Value("${alochat.telegram.api-base-url:https://api.telegram.org}") String apiBaseUrl
    ) {
        this.telegramBotCredentials = new TelegramBotCredentials(botToken, botUsername, apiBaseUrl);
    }

    @Override
    public TelegramBotCredentials getCredentials() {
        if (telegramBotCredentials.botToken() == null || telegramBotCredentials.botToken().isBlank()) {
            throw new IllegalStateException("Missing Telegram bot token configuration");
        }
        return telegramBotCredentials;
    }
}
