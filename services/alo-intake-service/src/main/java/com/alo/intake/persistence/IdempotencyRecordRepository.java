package com.alo.intake.persistence;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@Repository
public class IdempotencyRecordRepository {
    private final DynamoDbTable<IdempotencyItem> table;

    public IdempotencyRecordRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${alo.dynamodb.tables.idempotency:alo-idempotency}") String tableName
    ) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(IdempotencyItem.class));
    }

    public boolean tryCreate(IdempotencyItem item) {
        try {
            table.putItem(PutItemEnhancedRequest.builder(IdempotencyItem.class)
                    .item(item)
                    .conditionExpression(Expression.builder()
                            .expression("attribute_not_exists(idempotencyKey)")
                            .build())
                    .build());
            return true;
        } catch (ConditionalCheckFailedException exception) {
            return false;
        }
    }

    public Optional<IdempotencyItem> findByKey(String idempotencyKey) {
        return Optional.ofNullable(table.getItem(r -> r.key(key -> key.partitionValue(idempotencyKey))));
    }
}
