package com.alo.profile.service;

import com.alo.contracts.assessment.RegulatoryProfile;
import com.alo.contracts.events.AssessmentIntakeReceivedEvent;
import com.alo.contracts.events.KafkaTopics;
import com.alo.contracts.events.ProfileResolvedEvent;
import com.alo.support.kafka.ResilientKafkaPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class ProfileResolvedEventPublisher {
    private final ResilientKafkaPublisher resilientKafkaPublisher;

    public ProfileResolvedEventPublisher(ResilientKafkaPublisher resilientKafkaPublisher) {
        this.resilientKafkaPublisher = resilientKafkaPublisher;
    }

    public void publish(AssessmentIntakeReceivedEvent sourceEvent, RegulatoryProfile regulatoryProfile) {
        ProfileResolvedEvent event = new ProfileResolvedEvent(
                UUID.randomUUID().toString(),
                sourceEvent.traceId(),
                sourceEvent.assessment(),
                regulatoryProfile,
                Instant.now()
        );
        resilientKafkaPublisher.send(KafkaTopics.ASSESSMENT_PROFILE_RESOLVED, sourceEvent.assessment().assessmentId(), event);
    }
}
