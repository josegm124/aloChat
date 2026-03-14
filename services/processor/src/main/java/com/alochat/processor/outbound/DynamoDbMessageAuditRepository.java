package com.alochat.processor.outbound;

import com.alochat.contracts.message.MessageEnvelope;
import com.alochat.processor.port.MessageAuditRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

@Repository
public class DynamoDbMessageAuditRepository implements MessageAuditRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public DynamoDbMessageAuditRepository(
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

        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(tableName)
                        .item(item)
                        .build()
        );
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
