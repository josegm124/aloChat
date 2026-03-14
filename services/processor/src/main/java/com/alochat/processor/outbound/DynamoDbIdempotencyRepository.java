package com.alochat.processor.outbound;

import com.alochat.contracts.message.MessageEnvelope;
import com.alochat.processor.port.IdempotencyRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

@Repository
public class DynamoDbIdempotencyRepository implements IdempotencyRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public DynamoDbIdempotencyRepository(
            DynamoDbClient dynamoDbClient,
            @Value("${alochat.dynamodb.idempotency-table}") String tableName
    ) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    @Override
    public boolean register(MessageEnvelope envelope) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("idempotencyKey", AttributeValue.fromS(envelope.idempotencyKey()));
        item.put("messageId", AttributeValue.fromS(envelope.messageId()));
        item.put("conversationId", AttributeValue.fromS(nullSafe(envelope.conversationId())));
        item.put("channel", AttributeValue.fromS(envelope.channel().name()));
        item.put("status", AttributeValue.fromS(envelope.status().name()));
        item.put("createdAt", AttributeValue.fromS(Instant.now().toString()));

        PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .conditionExpression("attribute_not_exists(idempotencyKey)")
                .item(item)
                .build();
        try {
            dynamoDbClient.putItem(request);
            return true;
        } catch (ConditionalCheckFailedException exception) {
            return false;
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
