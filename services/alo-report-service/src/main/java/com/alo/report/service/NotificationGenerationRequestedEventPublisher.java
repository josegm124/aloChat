package com.alo.report.service;

import com.alo.contracts.assessment.GeneratedAssessmentReport;
import com.alo.report.api.PublishedReportResponse;
import com.alo.contracts.events.KafkaTopics;
import com.alo.contracts.events.NotificationGenerationRequestedEvent;
import com.alo.support.kafka.ResilientKafkaPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class NotificationGenerationRequestedEventPublisher {
    private final ResilientKafkaPublisher resilientKafkaPublisher;

    public NotificationGenerationRequestedEventPublisher(
            ResilientKafkaPublisher resilientKafkaPublisher
    ) {
        this.resilientKafkaPublisher = resilientKafkaPublisher;
    }

    public void publish(String traceId, String tenantId, PublishedReportResponse publishedReportResponse) {
        NotificationGenerationRequestedEvent event = new NotificationGenerationRequestedEvent(
                UUID.randomUUID().toString(),
                traceId,
                publishedReportResponse.generatedAssessmentReport(),
                tenantId,
                List.of(defaultRecipient(publishedReportResponse.generatedAssessmentReport())),
                publishedReportResponse.reportAccessUrl(),
                Instant.now()
        );
        resilientKafkaPublisher.send(
                KafkaTopics.ASSESSMENT_NOTIFICATION_REQUESTED,
                publishedReportResponse.generatedAssessmentReport().assessmentId(),
                event
        );
    }

    private String defaultRecipient(GeneratedAssessmentReport generatedAssessmentReport) {
        return "notifications+" + generatedAssessmentReport.assessmentId() + "@alo.local";
    }
}
