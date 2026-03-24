package com.alo.retry.messaging;

import com.alo.retry.service.RetryRepublisherService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RetryTopicKafkaListener {
    private final RetryRepublisherService retryRepublisherService;

    public RetryTopicKafkaListener(RetryRepublisherService retryRepublisherService) {
        this.retryRepublisherService = retryRepublisherService;
    }

    @KafkaListener(
            topics = "${alo.kafka.topics.retry-short}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onShortRetry(ConsumerRecord<String, Object> record) {
        retryRepublisherService.republishShort(record);
    }

    @KafkaListener(
            topics = "${alo.kafka.topics.retry-long}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onLongRetry(ConsumerRecord<String, Object> record) {
        retryRepublisherService.republishLong(record);
    }
}
