package com.alo.dataset.service;

import com.alo.contracts.assessment.ArtifactRef;
import com.alo.contracts.events.DatasetAnalysisRequestedEvent;
import com.alo.contracts.events.KafkaTopics;
import com.alo.contracts.events.ProfileResolvedEvent;
import com.alo.support.kafka.ResilientKafkaPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class DatasetAnalysisRequestedEventPublisher {
    private final ResilientKafkaPublisher resilientKafkaPublisher;

    public DatasetAnalysisRequestedEventPublisher(ResilientKafkaPublisher resilientKafkaPublisher) {
        this.resilientKafkaPublisher = resilientKafkaPublisher;
    }

    public void publish(ProfileResolvedEvent sourceEvent, ArtifactRef artifact) {
        DatasetAnalysisRequestedEvent event = new DatasetAnalysisRequestedEvent(
                UUID.randomUUID().toString(),
                sourceEvent.traceId(),
                sourceEvent.assessment(),
                sourceEvent.regulatoryProfile(),
                artifact,
                Instant.now()
        );
        resilientKafkaPublisher.send(KafkaTopics.ASSESSMENT_DATASET_REQUESTED, sourceEvent.assessment().assessmentId(), event);
    }
}
