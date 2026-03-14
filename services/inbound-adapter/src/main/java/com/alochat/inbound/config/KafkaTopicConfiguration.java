package com.alochat.inbound.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfiguration {

    @Bean
    NewTopic normalizedTopic() {
        return TopicBuilder.name("messages.ingress.normalized").partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic aiTopic() {
        return TopicBuilder.name("messages.processing.ai").partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic outboundTopic() {
        return TopicBuilder.name("messages.outbound.dispatch").partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic retryShortTopic() {
        return TopicBuilder.name("messages.retry.short").partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic retryLongTopic() {
        return TopicBuilder.name("messages.retry.long").partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic dlqTopic() {
        return TopicBuilder.name("messages.dlq").partitions(1).replicas(1).build();
    }
}
