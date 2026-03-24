package com.alo.intake.persistence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Repository
public class SubmissionRecordRepository {
    private final DynamoDbTable<SubmissionItem> table;

    public SubmissionRecordRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${alo.dynamodb.tables.submissions:alo-submissions}") String tableName
    ) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(SubmissionItem.class));
    }

    public void save(SubmissionItem item) {
        table.putItem(item);
    }
}
