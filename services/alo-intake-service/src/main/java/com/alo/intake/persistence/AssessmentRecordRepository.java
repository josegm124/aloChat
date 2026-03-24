package com.alo.intake.persistence;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Repository
public class AssessmentRecordRepository {
    private final DynamoDbTable<AssessmentItem> table;

    public AssessmentRecordRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${alo.dynamodb.tables.assessments:alo-assessments}") String tableName
    ) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(AssessmentItem.class));
    }

    public void save(AssessmentItem item) {
        table.putItem(item);
    }

    public Optional<AssessmentItem> findById(String assessmentId) {
        return Optional.ofNullable(table.getItem(r -> r.key(key -> key.partitionValue(assessmentId))));
    }
}
