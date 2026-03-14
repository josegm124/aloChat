package com.alochat.outbound.channel.telegram;

import com.alochat.contracts.message.MessageEnvelope;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TelegramDispatchClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramDispatchClient.class);

    private final RestClient restClient;
    private final TelegramBotCredentialsProvider credentialsProvider;

    public TelegramDispatchClient(
            RestClient.Builder restClientBuilder,
            TelegramBotCredentialsProvider credentialsProvider
    ) {
        this.restClient = restClientBuilder.build();
        this.credentialsProvider = credentialsProvider;
    }

    @Retry(name = "telegramDispatch", fallbackMethod = "fallbackSendMessage")
    @CircuitBreaker(name = "telegramDispatch", fallbackMethod = "fallbackSendMessage")
    public void sendMessage(MessageEnvelope envelope) {
        TelegramBotCredentials credentials = credentialsProvider.getCredentials();
        TelegramSendMessageRequest request = new TelegramSendMessageRequest(
                envelope.userId(),
                envelope.content().text() == null ? "" : envelope.content().text()
        );

        restClient.post()
                .uri(credentials.apiBaseUrl() + "/bot" + credentials.botToken() + "/sendMessage")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public void fallbackSendMessage(MessageEnvelope envelope, Throwable throwable) {
        log.error(
                "Telegram dispatch failed after retries messageId={} userId={} error={}",
                envelope.messageId(),
                envelope.userId(),
                throwable.getMessage()
        );
        throw new IllegalStateException("Telegram dispatch unavailable", throwable);
    }
}
