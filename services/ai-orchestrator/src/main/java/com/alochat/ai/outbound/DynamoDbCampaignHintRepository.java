package com.alochat.ai.outbound;

import com.alochat.ai.model.CampaignHint;
import com.alochat.ai.port.CampaignHintRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

@Repository
public class DynamoDbCampaignHintRepository implements CampaignHintRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public DynamoDbCampaignHintRepository(
            DynamoDbClient dynamoDbClient,
            @Value("${alochat.dynamodb.campaign-hint-table}") String tableName
    ) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    @Override
    public void save(CampaignHint campaignHint) {
        if (campaignHint == null) {
            return;
        }
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("hintId", AttributeValue.fromS(campaignHint.hintId()));
        item.put("memoryKey", AttributeValue.fromS(campaignHint.memoryKey()));
        item.put("tenantId", AttributeValue.fromS(nullSafe(campaignHint.tenantId())));
        item.put("channel", AttributeValue.fromS(campaignHint.channel()));
        item.put("conversationId", AttributeValue.fromS(nullSafe(campaignHint.conversationId())));
        item.put("userId", AttributeValue.fromS(nullSafe(campaignHint.userId())));
        item.put("hintType", AttributeValue.fromS(campaignHint.hintType()));
        item.put("reason", AttributeValue.fromS(nullSafe(campaignHint.reason())));
        item.put("createdAt", AttributeValue.fromS(campaignHint.createdAt().toString()));
        item.put("triggerAt", AttributeValue.fromS(campaignHint.triggerAt().toString()));
        if (!campaignHint.relatedProducts().isEmpty()) {
            item.put("relatedProducts", AttributeValue.fromSs(campaignHint.relatedProducts()));
        }
        if (!campaignHint.relatedProductPrices().isEmpty()) {
            item.put("relatedProductPrices", AttributeValue.fromM(stringMap(campaignHint.relatedProductPrices())));
        }
        if (campaignHint.lastTriggeredAt() != null) {
            item.put("lastTriggeredAt", AttributeValue.fromS(campaignHint.lastTriggeredAt().toString()));
        }

        dynamoDbClient.putItem(PutItemRequest.builder().tableName(tableName).item(item).build());
    }

    @Override
    public List<CampaignHint> findCandidates(Instant now, int limit) {
        List<CampaignHint> results = new ArrayList<>();
        dynamoDbClient.scanPaginator(ScanRequest.builder().tableName(tableName).limit(Math.max(limit, 10)).build())
                .items()
                .forEach(item -> {
                    if (results.size() >= limit) {
                        return;
                    }
                    CampaignHint hint = toHint(item);
                    if (hint == null) {
                        return;
                    }
                    if (isCandidate(now, hint)) {
                        results.add(hint);
                    }
                });
        return List.copyOf(results);
    }

    @Override
    public void markTriggered(String hintId, Instant triggeredAt, Map<String, String> relatedProductPrices) {
        Map<String, String> safePrices = relatedProductPrices == null ? Map.of() : Map.copyOf(relatedProductPrices);
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":triggeredAt", AttributeValue.fromS(triggeredAt.toString()));
        values.put(":empty", AttributeValue.fromS(""));
        String updateExpression = "SET lastTriggeredAt = :triggeredAt";
        if (!safePrices.isEmpty()) {
            values.put(":relatedProductPrices", AttributeValue.fromM(stringMap(safePrices)));
            updateExpression += ", relatedProductPrices = :relatedProductPrices";
        }

        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("hintId", AttributeValue.fromS(hintId)))
                .updateExpression(updateExpression)
                .expressionAttributeValues(values)
                .build());
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private boolean isCandidate(Instant now, CampaignHint hint) {
        if (hint == null || hint.triggerAt() == null) {
            return false;
        }
        if ("REPLENISHMENT_FOLLOW_UP".equals(hint.hintType())) {
            return hint.lastTriggeredAt() == null && !hint.triggerAt().isAfter(now);
        }
        return "DISCOUNT_WATCH".equals(hint.hintType()) && !hint.relatedProducts().isEmpty();
    }

    private CampaignHint toHint(Map<String, AttributeValue> item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        return new CampaignHint(
                stringValue(item, "hintId"),
                stringValue(item, "memoryKey"),
                stringValue(item, "tenantId"),
                stringValue(item, "channel"),
                stringValue(item, "conversationId"),
                stringValue(item, "userId"),
                stringValue(item, "hintType"),
                stringValue(item, "reason"),
                item.containsKey("relatedProducts") ? item.get("relatedProducts").ss() : List.of(),
                item.containsKey("relatedProductPrices") ? flattenMap(item.get("relatedProductPrices").m()) : Map.of(),
                parseInstant(item, "createdAt"),
                parseInstant(item, "triggerAt"),
                parseNullableInstant(item, "lastTriggeredAt")
        );
    }

    private Instant parseInstant(Map<String, AttributeValue> item, String key) {
        String value = stringValue(item, key);
        return value.isBlank() ? null : Instant.parse(value);
    }

    private Instant parseNullableInstant(Map<String, AttributeValue> item, String key) {
        return parseInstant(item, key);
    }

    private String stringValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value == null || value.s() == null ? "" : value.s();
    }

    private Map<String, AttributeValue> stringMap(Map<String, String> source) {
        Map<String, AttributeValue> values = new HashMap<>();
        source.forEach((key, value) -> values.put(key, AttributeValue.fromS(value)));
        return values;
    }

    private Map<String, String> flattenMap(Map<String, AttributeValue> source) {
        Map<String, String> values = new HashMap<>();
        source.forEach((key, value) -> values.put(key, value.s()));
        return Map.copyOf(values);
    }
}
