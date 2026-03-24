package com.alo.dataset.service;

import com.alo.contracts.assessment.AssessmentEnvelope;
import com.alo.contracts.assessment.DatasetAnalysisResult;
import com.alo.contracts.assessment.RegulatoryProfile;
import com.alo.contracts.events.DatasetAnalysisCompletedEvent;
import com.alo.contracts.events.KafkaTopics;
import com.alo.support.kafka.ResilientKafkaPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class DatasetAnalysisCompletedEventPublisher {
    private final ResilientKafkaPublisher resilientKafkaPublisher;

    public DatasetAnalysisCompletedEventPublisher(ResilientKafkaPublisher resilientKafkaPublisher) {
        this.resilientKafkaPublisher = resilientKafkaPublisher;
    }

    public void publish(
            String traceId,
            AssessmentEnvelope assessment,
            RegulatoryProfile regulatoryProfile,
            DatasetAnalysisResult result
    ) {
        DatasetAnalysisCompletedEvent event = new DatasetAnalysisCompletedEvent(
                UUID.randomUUID().toString(),
                traceId,
                assessment,
                regulatoryProfile,
                result,
                Instant.now()
        );
        resilientKafkaPublisher.send(KafkaTopics.ASSESSMENT_DATASET_COMPLETED, result.assessmentId(), event);
    }
}
