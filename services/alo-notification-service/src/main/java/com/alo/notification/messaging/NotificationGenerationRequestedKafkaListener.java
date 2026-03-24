package com.alo.notification.messaging;

import com.alo.contracts.assessment.GeneratedNotification;
import com.alo.contracts.events.NotificationGenerationRequestedEvent;
import com.alo.notification.service.NotificationCompletedEventPublisher;
import com.alo.notification.service.NotificationGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationGenerationRequestedKafkaListener {
    private static final Logger log = LoggerFactory.getLogger(NotificationGenerationRequestedKafkaListener.class);
    private final NotificationGenerationService notificationGenerationService;
    private final NotificationCompletedEventPublisher notificationCompletedEventPublisher;

    public NotificationGenerationRequestedKafkaListener(
            NotificationGenerationService notificationGenerationService,
            NotificationCompletedEventPublisher notificationCompletedEventPublisher
    ) {
        this.notificationGenerationService = notificationGenerationService;
        this.notificationCompletedEventPublisher = notificationCompletedEventPublisher;
    }

    @KafkaListener(
            topics = "${alo.kafka.topics.assessment-notification-requested}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onNotificationGenerationRequested(NotificationGenerationRequestedEvent event) {
        log.info(
                "notification generation started assessmentId={} traceId={} recipients={}",
                event.generatedAssessmentReport().assessmentId(),
                event.traceId(),
                event.recipients().size()
        );
        GeneratedNotification notification = notificationGenerationService.generate(event);
        log.info(
                "notification generated assessmentId={} channel={} recipients={} status={} messageId={}",
                notification.assessmentId(),
                notification.channel(),
                notification.recipients().size(),
                notification.deliveryStatus(),
                notification.providerMessageId()
        );
        notificationCompletedEventPublisher.publish(event.traceId(), notification);
        log.info(
                "notification completed event published assessmentId={} traceId={}",
                notification.assessmentId(),
                event.traceId()
        );
    }
}
