package com.alochat.inbound.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

@Configuration
public class AwsClientConfiguration {

    @Bean
    public DynamoDbClient dynamoDbClient(@Value("${alochat.aws.region}") String region) {
        return DynamoDbClient.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean
    public SecretsManagerClient secretsManagerClient(@Value("${alochat.aws.region}") String region) {
        return SecretsManagerClient.builder()
                .region(Region.of(region))
                .build();
    }
}
