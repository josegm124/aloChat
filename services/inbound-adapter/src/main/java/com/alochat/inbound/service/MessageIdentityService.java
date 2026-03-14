package com.alochat.inbound.service;

import com.alochat.contracts.message.Channel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MessageIdentityService {

    private final ObjectMapper objectMapper;

    public MessageIdentityService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String newMessageId() {
        return UUID.randomUUID().toString();
    }

    public String resolveTraceId(Map<String, String> headers) {
        String traceId = headers.get("x-trace-id");
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }

        String traceParent = headers.get("traceparent");
        if (traceParent != null && !traceParent.isBlank()) {
            String[] parts = traceParent.split("-");
            if (parts.length >= 4 && !parts[1].isBlank()) {
                return parts[1];
            }
        }

        return firstNonBlank(
                headers.get("x-request-id"),
                UUID.randomUUID().toString()
        );
    }

    public String resolveExternalRequestId(Map<String, String> headers) {
        return firstNonBlank(
                headers.get("x-request-id"),
                headers.get("x-correlation-id"),
                UUID.randomUUID().toString()
        );
    }

    public String buildIdempotencyKey(Channel channel, String externalMessageId, JsonNode payload) {
        String prefix = channel.name().toLowerCase();
        if (externalMessageId != null && !externalMessageId.isBlank()) {
            return prefix + ":" + externalMessageId;
        }

        return prefix + ":" + digestPayload(payload);
    }

    public String externalKey(String scope, String externalMessageId) {
        return firstNonBlank(scope, "unknown-scope") + ":" + firstNonBlank(externalMessageId, "unknown-message");
    }

    public String partitionKey(String conversationId, String messageId) {
        return firstNonBlank(conversationId, messageId, UUID.randomUUID().toString());
    }

    private String digestPayload(JsonNode payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(objectMapper.writeValueAsBytes(payload));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | JsonProcessingException exception) {
            throw new IllegalStateException("Unable to create payload digest", exception);
        }
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Expected at least one identifier candidate");
    }
}
