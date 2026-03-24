package com.alo.orchestrator.service;

import com.alo.contracts.assessment.ConsolidatedAssessmentResult;
import com.alo.contracts.events.KafkaTopics;
import com.alo.contracts.events.ReportGenerationRequestedEvent;
import com.alo.support.kafka.ResilientKafkaPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class ReportGenerationRequestedEventPublisher {
    private final ResilientKafkaPublisher resilientKafkaPublisher;

    public ReportGenerationRequestedEventPublisher(ResilientKafkaPublisher resilientKafkaPublisher) {
        this.resilientKafkaPublisher = resilientKafkaPublisher;
    }

    public void publish(String traceId, ConsolidatedAssessmentResult consolidatedAssessmentResult) {
        ReportGenerationRequestedEvent event = new ReportGenerationRequestedEvent(
                UUID.randomUUID().toString(),
                traceId,
                consolidatedAssessmentResult,
                Instant.now()
        );
        resilientKafkaPublisher.send(
                KafkaTopics.ASSESSMENT_REPORT_REQUESTED,
                consolidatedAssessmentResult.assessmentId(),
                event
        );
    }
}
