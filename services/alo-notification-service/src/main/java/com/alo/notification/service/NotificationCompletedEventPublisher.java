package com.alo.notification.service;

import com.alo.contracts.assessment.GeneratedNotification;
import com.alo.contracts.events.KafkaTopics;
import com.alo.contracts.events.NotificationCompletedEvent;
import com.alo.support.kafka.ResilientKafkaPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class NotificationCompletedEventPublisher {
    private final ResilientKafkaPublisher resilientKafkaPublisher;

    public NotificationCompletedEventPublisher(ResilientKafkaPublisher resilientKafkaPublisher) {
        this.resilientKafkaPublisher = resilientKafkaPublisher;
    }

    public void publish(String traceId, GeneratedNotification generatedNotification) {
        NotificationCompletedEvent event = new NotificationCompletedEvent(
                UUID.randomUUID().toString(),
                traceId,
                generatedNotification,
                Instant.now()
        );
        resilientKafkaPublisher.send(
                KafkaTopics.ASSESSMENT_NOTIFICATION_COMPLETED,
                generatedNotification.assessmentId(),
                event
        );
    }
}
