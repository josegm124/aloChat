package com.alochat.inbound.outbound;

import com.alochat.contracts.message.Channel;
import com.alochat.contracts.message.MessageEnvelope;
import com.alochat.contracts.message.MessageStatus;
import com.alochat.inbound.api.MessageStatusResponse;
import com.alochat.inbound.port.InboundMessageAuditRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

@Repository
public class DynamoDbInboundMessageAuditRepository implements InboundMessageAuditRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public DynamoDbInboundMessageAuditRepository(
            DynamoDbClient dynamoDbClient,
            @Value("${alochat.dynamodb.message-audit-table}") String tableName
    ) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    @Override
    public void save(MessageEnvelope envelope) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("messageId", AttributeValue.fromS(envelope.messageId()));
        item.put("conversationId", AttributeValue.fromS(nullSafe(envelope.conversationId())));
        item.put("idempotencyKey", AttributeValue.fromS(envelope.idempotencyKey()));
        item.put("channel", AttributeValue.fromS(envelope.channel().name()));
        item.put("tenantId", AttributeValue.fromS(nullSafe(envelope.tenantId())));
        item.put("userId", AttributeValue.fromS(nullSafe(envelope.userId())));
        item.put("status", AttributeValue.fromS(envelope.status().name()));
        item.put("receivedAt", AttributeValue.fromS(envelope.receivedAt().toString()));
        item.put("updatedAt", AttributeValue.fromS(Instant.now().toString()));
        item.put("contentText", AttributeValue.fromS(nullSafe(envelope.content().text())));

        dynamoDbClient.putItem(PutItemRequest.builder().tableName(tableName).item(item).build());
    }

    @Override
    public Optional<MessageStatusResponse> findByMessageId(String messageId) {
        Map<String, AttributeValue> key = Map.of("messageId", AttributeValue.fromS(messageId));
        Map<String, AttributeValue> item = dynamoDbClient.getItem(
                GetItemRequest.builder()
                        .tableName(tableName)
                        .key(key)
                        .build()
        ).item();

        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new MessageStatusResponse(
                item.get("messageId").s(),
                readString(item, "conversationId"),
                Channel.valueOf(readString(item, "channel")),
                MessageStatus.valueOf(readString(item, "status")),
                Instant.parse(readString(item, "updatedAt")),
                readString(item, "contentText")
        ));
    }

    private String readString(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value == null ? "" : value.s();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
