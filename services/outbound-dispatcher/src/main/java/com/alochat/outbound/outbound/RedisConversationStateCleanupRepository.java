package com.alochat.outbound.outbound;

import com.alochat.contracts.message.MessageEnvelope;
import com.alochat.outbound.port.ConversationStateCleanupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisConversationStateCleanupRepository implements ConversationStateCleanupRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisConversationStateCleanupRepository.class);

    private final StringRedisTemplate redisTemplate;

    public RedisConversationStateCleanupRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void clear(MessageEnvelope envelope) {
        String conversationId = envelope.conversationId() == null || envelope.conversationId().isBlank()
                ? envelope.messageId()
                : envelope.conversationId();
        String key = "conversation:" + envelope.channel().name().toLowerCase() + ":" + conversationId;
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException exception) {
            log.warn("Redis conversation state cleanup failed messageId={} conversationId={} error={}",
                    envelope.messageId(),
                    envelope.conversationId(),
                    exception.getMessage());
        }
    }
}
