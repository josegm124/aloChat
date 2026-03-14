package com.alochat.contracts.message;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MessageEnvelope(
        String messageId,
        String idempotencyKey,
        String traceId,
        Channel channel,
        String tenantId,
        String externalMessageId,
        String conversationId,
        String userId,
        Instant receivedAt,
        NormalizedContent content,
        List<Attachment> attachments,
        Map<String, String> metadata,
        String rawPayloadRef,
        MessageStatus status
) {
    public MessageEnvelope {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public MessageEnvelope withStatus(MessageStatus newStatus) {
        return new MessageEnvelope(
                messageId,
                idempotencyKey,
                traceId,
                channel,
                tenantId,
                externalMessageId,
                conversationId,
                userId,
                receivedAt,
                content,
                attachments,
                metadata,
                rawPayloadRef,
                newStatus
        );
    }

    public MessageEnvelope withContent(NormalizedContent newContent) {
        return new MessageEnvelope(
                messageId,
                idempotencyKey,
                traceId,
                channel,
                tenantId,
                externalMessageId,
                conversationId,
                userId,
                receivedAt,
                newContent,
                attachments,
                metadata,
                rawPayloadRef,
                status
        );
    }

    public MessageEnvelope withMetadata(Map<String, String> newMetadata) {
        return new MessageEnvelope(
                messageId,
                idempotencyKey,
                traceId,
                channel,
                tenantId,
                externalMessageId,
                conversationId,
                userId,
                receivedAt,
                content,
                attachments,
                newMetadata,
                rawPayloadRef,
                status
        );
    }
}
