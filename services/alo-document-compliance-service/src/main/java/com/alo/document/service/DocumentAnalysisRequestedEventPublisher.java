package com.alo.document.service;

import com.alo.contracts.assessment.ArtifactRef;
import com.alo.contracts.events.DocumentAnalysisRequestedEvent;
import com.alo.contracts.events.KafkaTopics;
import com.alo.contracts.events.ProfileResolvedEvent;
import com.alo.support.kafka.ResilientKafkaPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class DocumentAnalysisRequestedEventPublisher {
    private final ResilientKafkaPublisher resilientKafkaPublisher;

    public DocumentAnalysisRequestedEventPublisher(ResilientKafkaPublisher resilientKafkaPublisher) {
        this.resilientKafkaPublisher = resilientKafkaPublisher;
    }

    public void publish(ProfileResolvedEvent sourceEvent, ArtifactRef artifact) {
        DocumentAnalysisRequestedEvent event = new DocumentAnalysisRequestedEvent(
                UUID.randomUUID().toString(),
                sourceEvent.traceId(),
                sourceEvent.assessment(),
                sourceEvent.regulatoryProfile(),
                artifact,
                Instant.now()
        );
        resilientKafkaPublisher.send(KafkaTopics.ASSESSMENT_DOCUMENT_REQUESTED, sourceEvent.assessment().assessmentId(), event);
    }
}
