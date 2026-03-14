package com.alochat.ai.outbound;

import com.alochat.ai.model.CampaignHint;
import com.alochat.ai.port.CampaignHintRepository;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

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

        dynamoDbClient.putItem(PutItemRequest.builder().tableName(tableName).item(item).build());
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
