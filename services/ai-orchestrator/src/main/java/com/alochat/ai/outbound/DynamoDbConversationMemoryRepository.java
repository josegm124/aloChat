package com.alochat.ai.outbound;

import com.alochat.ai.model.ConversationMemory;
import com.alochat.ai.port.ConversationMemoryRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

@Repository
@ConditionalOnMissingBean(ConversationMemoryRepository.class)
public class DynamoDbConversationMemoryRepository implements ConversationMemoryRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public DynamoDbConversationMemoryRepository(
            DynamoDbClient dynamoDbClient,
            @Value("${alochat.dynamodb.conversation-memory-table}") String tableName
    ) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    @Override
    public Optional<ConversationMemory> findByMemoryKey(String memoryKey) {
        GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("memoryKey", AttributeValue.fromS(memoryKey)))
                .build());

        if (!response.hasItem() || response.item().isEmpty()) {
            return Optional.empty();
        }

        Map<String, AttributeValue> item = response.item();
        return Optional.of(new ConversationMemory(
                stringValue(item, "memoryKey"),
                stringValue(item, "tenantId"),
                stringValue(item, "channel"),
                stringValue(item, "conversationId"),
                stringValue(item, "userId"),
                stringValue(item, "summary"),
                stringValue(item, "lastQuestion"),
                item.containsKey("trackedProducts") ? item.get("trackedProducts").ss() : java.util.List.of(),
                item.containsKey("interestTags") ? item.get("interestTags").ss() : java.util.List.of(),
                parseInstant(item, "lastInteractionAt"),
                parseNullableInstant(item, "followUpAt"),
                stringValue(item, "followUpReason")
        ));
    }

    @Override
    public void save(ConversationMemory conversationMemory) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("memoryKey", AttributeValue.fromS(conversationMemory.memoryKey()));
        item.put("tenantId", AttributeValue.fromS(nullSafe(conversationMemory.tenantId())));
        item.put("channel", AttributeValue.fromS(conversationMemory.channel()));
        item.put("conversationId", AttributeValue.fromS(nullSafe(conversationMemory.conversationId())));
        item.put("userId", AttributeValue.fromS(nullSafe(conversationMemory.userId())));
        item.put("summary", AttributeValue.fromS(nullSafe(conversationMemory.summary())));
        item.put("lastQuestion", AttributeValue.fromS(nullSafe(conversationMemory.lastQuestion())));
        item.put("lastInteractionAt", AttributeValue.fromS(conversationMemory.lastInteractionAt().toString()));
        item.put("followUpReason", AttributeValue.fromS(nullSafe(conversationMemory.followUpReason())));
        if (conversationMemory.followUpAt() != null) {
            item.put("followUpAt", AttributeValue.fromS(conversationMemory.followUpAt().toString()));
        }
        if (!conversationMemory.trackedProducts().isEmpty()) {
            item.put("trackedProducts", AttributeValue.fromSs(conversationMemory.trackedProducts()));
        }
        if (!conversationMemory.interestTags().isEmpty()) {
            item.put("interestTags", AttributeValue.fromSs(conversationMemory.interestTags()));
        }

        dynamoDbClient.putItem(PutItemRequest.builder().tableName(tableName).item(item).build());
    }

    private Instant parseInstant(Map<String, AttributeValue> item, String key) {
        return Instant.parse(stringValue(item, key));
    }

    private Instant parseNullableInstant(Map<String, AttributeValue> item, String key) {
        String value = stringValue(item, key);
        return value.isBlank() ? null : Instant.parse(value);
    }

    private String stringValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value == null || value.s() == null ? "" : value.s();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
