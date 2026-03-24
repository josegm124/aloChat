package com.alo.document.service;

import com.alo.contracts.assessment.AssessmentEnvelope;
import com.alo.contracts.assessment.DocumentAnalysisResult;
import com.alo.contracts.assessment.RegulatoryProfile;
import com.alo.contracts.events.DocumentAnalysisCompletedEvent;
import com.alo.contracts.events.KafkaTopics;
import com.alo.support.kafka.ResilientKafkaPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class DocumentAnalysisCompletedEventPublisher {
    private final ResilientKafkaPublisher resilientKafkaPublisher;

    public DocumentAnalysisCompletedEventPublisher(ResilientKafkaPublisher resilientKafkaPublisher) {
        this.resilientKafkaPublisher = resilientKafkaPublisher;
    }

    public void publish(
            String traceId,
            AssessmentEnvelope assessment,
            RegulatoryProfile regulatoryProfile,
            DocumentAnalysisResult result
    ) {
        DocumentAnalysisCompletedEvent event = new DocumentAnalysisCompletedEvent(
                UUID.randomUUID().toString(),
                traceId,
                assessment,
                regulatoryProfile,
                result,
                Instant.now()
        );
        resilientKafkaPublisher.send(KafkaTopics.ASSESSMENT_DOCUMENT_COMPLETED, result.assessmentId(), event);
    }
}
