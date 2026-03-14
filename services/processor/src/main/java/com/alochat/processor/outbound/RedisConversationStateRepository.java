package com.alochat.processor.outbound;

import com.alochat.contracts.message.MessageEnvelope;
import com.alochat.processor.port.ConversationStateRepository;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisConversationStateRepository implements ConversationStateRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisConversationStateRepository.class);

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public RedisConversationStateRepository(
            StringRedisTemplate redisTemplate,
            @Value("${alochat.redis.conversation-ttl}") Duration ttl
    ) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    @Override
    public void store(MessageEnvelope envelope) {
        String key = "conversation:" + envelope.channel().name().toLowerCase() + ":" + safeConversationId(envelope);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("messageId", envelope.messageId());
        values.put("traceId", envelope.traceId());
        values.put("idempotencyKey", envelope.idempotencyKey());
        values.put("channel", envelope.channel().name());
        values.put("tenantId", nullSafe(envelope.tenantId()));
        values.put("conversationId", nullSafe(envelope.conversationId()));
        values.put("userId", nullSafe(envelope.userId()));
        values.put("status", envelope.status().name());
        values.put("content", envelope.content().text() == null ? "" : envelope.content().text());
        values.put("receivedAt", envelope.receivedAt().toString());

        try {
            redisTemplate.opsForHash().putAll(key, values);
            redisTemplate.expire(key, ttl);
        } catch (RuntimeException exception) {
            log.warn("Redis conversation state write failed messageId={} conversationId={} error={}",
                    envelope.messageId(),
                    envelope.conversationId(),
                    exception.getMessage());
        }
    }

    private String safeConversationId(MessageEnvelope envelope) {
        return envelope.conversationId() == null || envelope.conversationId().isBlank()
                ? envelope.messageId()
                : envelope.conversationId();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
